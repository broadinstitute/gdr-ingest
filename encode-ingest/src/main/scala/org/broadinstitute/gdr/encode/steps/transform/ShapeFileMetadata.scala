package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import cats.kernel.Monoid
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.EncodeFields
import org.broadinstitute.gdr.encode.client.EncodeClient
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class ShapeFileMetadata(fileMetadata: File, override protected val out: File)
    extends IngestStep {
  import org.broadinstitute.gdr.encode.EncodeFields._
  import ShapeFileMetadata._

  override def process[F[_]: Effect]: Stream[F, Unit] =
    fileGraph.flatMap { graph =>
      IngestStep.readJsonArray(fileMetadata).map(addFields(graph))
    }.unNone.map(_.filterKeys(RetainedFields.contains)).to(IngestStep.writeJsonArray(out))

  private def fileGraph[F[_]: Sync]: Stream[F, FileGraph] =
    IngestStep
      .readJsonArray(fileMetadata)
      .evalMap { file =>
        val newInfo = for {
          id <- file(EncodeIdField).flatMap(_.asString)
          fileType <- file("file_format").flatMap(_.asString)
        } yield {
          val replicateRef = file("replicate").flatMap(_.asString)
          val sourceFiles =
            file("derived_from").flatMap(_.asArray.map(_.flatMap(_.asString)))

          val fastqInfo = for {
            readCount <- file("read_count").flatMap(_.asNumber.flatMap(_.toLong))
            runType <- file("run_type").flatMap(_.asString)
          } yield {
            FastqInfo(readCount, runType == "paired-ended")
          }

          FileGraph(
            replicateRef.fold(Map.empty[String, String])(r => Map(id -> r)),
            sourceFiles.fold(Map.empty[String, Set[String]])(s => Map(id -> s.toSet)),
            fastqInfo
              .filter(_ => fileType.equals("fastq"))
              .fold(Map.empty[String, FastqInfo])(i => Map(id -> i)),
            Set(ShapeFileMetadata.extractFileId(id))
          )
        }
        Sync[F].fromOption(
          newInfo,
          new IllegalStateException(s"Expected fields not found in $file")
        )
      }
      .foldMonoid

  private def addFields(graph: FileGraph)(file: JsonObject): Option[JsonObject] = {
    for {
      id <- file(EncodeIdField).flatMap(_.asString)
      accession <- file(FileIdField).flatMap(_.asString)
      (replicateIds, fastqInfo) = exploreGraph(
        id,
        graph,
        Nil,
        Set.empty,
        FastqInfo.mon.empty,
        Set.empty
      )
      nonEmptyIds <- ensureReplicates(id, replicateIds)
      fileType <- file("file_type").flatMap(_.asString)
      sourceFiles <- file("derived_from")
        .flatMap(_.as[Set[String]].toOption)
        .map(_.map(ShapeFileMetadata.extractFileId))
    } yield {
      val sourceFilesFromExperiments = sourceFiles
        .intersect(graph.allFiles)
      val sourceReferences = sourceFiles.diff(sourceFilesFromExperiments)

      val generalFields = Map(
        ReplicateFkField -> nonEmptyIds.asJson,
        DerivedFromExperimentField -> sourceFilesFromExperiments.asJson,
        DerivedFromReferenceField -> sourceReferences.asJson,
        FileAccessionField -> accession.asJson,
        EncodeLinkField -> (EncodeClient.EncodeUri / accession).toString.asJson
      )

      val typeSpecificFields = fileType match {
        case "bam"   => bamFields(file, fastqInfo)
        case "fastq" => Map(RunTypeField -> graph.fastqInfos(id).pairedReads.asJson)
        case _       => Map.empty
      }

      (generalFields ++ typeSpecificFields).foldRight(file)(_ +: _)
    }
  }

  @scala.annotation.tailrec
  private def exploreGraph(
    id: String,
    graph: FileGraph,
    nextIds: List[String],
    replicateAcc: Set[String],
    info: FastqInfo,
    visited: Set[String]
  ): (Set[String], FastqInfo) = {

    val newNext = graph.fileToSources
      .get(id)
      .fold(nextIds)(nextIds ++ _)

    val newReplicates = graph.fileToReplicate
      .get(id)
      .fold(replicateAcc)(replicateAcc + _)

    val newInfo = graph.fastqInfos.get(id).fold(info)(info |+| _)

    newNext.dropWhile(visited.contains) match {
      case Nil => (newReplicates, newInfo)
      case next :: more =>
        exploreGraph(next, graph, more, newReplicates, newInfo, visited + id)
    }
  }

  private def ensureReplicates(
    fileId: String,
    replicateIds: Set[String]
  ): Option[Set[String]] =
    if (replicateIds.isEmpty) {
      logger.warn(s"Dropping file '$fileId', not linked to any replicate")
      None
    } else {
      Some(replicateIds)
    }

  private def bamFields(
    bamJson: JsonObject,
    fastqInfo: FastqInfo
  ): Map[String, Json] = {
    val qcFields = for {
      // Extract out QC container:
      notesBlob <- bamJson("notes").flatMap(_.asString)
      notesObj <- io.circe.jawn.parse(notesBlob).toOption.flatMap(_.asObject)
      qcObj <- notesObj("qc").flatMap(_.asObject).flatMap(_("qc")).flatMap(_.asObject)
      // Extract out QC values:
      aligned <- qcObj("mapped").flatMap(_.as[Array[Long]].toOption).map(_.head)
      duplicated <- qcObj("duplicates").flatMap(_.as[Array[Long]].toOption).map(_.head)
      total <- qcObj("in_total").flatMap(_.as[Array[Long]].toOption).map(_.head)
    } yield {
      Iterable(
        PercentAlignedField -> (aligned.toDouble / total).asJson,
        PercentDupsField -> (duplicated.toDouble / total).asJson
      )
    }

    Iterable
      .concat(
        Iterable(
          ReadCountField -> fastqInfo.readCount.asJson,
          RunTypeField -> fastqInfo.pairedReads.asJson
        ),
        bamJson("mapped_read_length").map(ReadLengthField -> _),
        qcFields.getOrElse(Iterable.empty)
      )
      .toMap
  }
}

object ShapeFileMetadata {
  val DerivedFromExperimentField = "derived_from_exp"
  val DerivedFromReferenceField = "derived_from_ref"
  val EncodeLinkField = "url"
  val FileIdField = "accession"
  val FileAccessionField = "file_accession"
  val PercentDupsField = "percent_duplicated"
  val PercentAlignedField = "percent_aligned"
  val ReadCountField = "read_count"
  val ReadLengthField = "read_length"
  val RunTypeField = "paired_run"

  val ReplicateRefsPrefix = "file"

  val ReplicateFkField =
    EncodeFields.joinedName("id", JoinReplicateMetadata.ReplicatePrefix)

  val RetainedFields = Set(
    "assembly",
    DerivedFromExperimentField,
    DerivedFromReferenceField,
    EncodeFields.EncodeIdField,
    EncodeLinkField,
    FileAccessionField,
    "file_format",
    "file_size",
    "file_type",
    "href",
    "md5sum",
    "output_type",
    PercentAlignedField,
    PercentDupsField,
    ReadCountField,
    ReadLengthField,
    ReplicateFkField,
    RunTypeField
  )

  private def extractFileId(fileRef: String): String =
    fileRef.drop(7).dropRight(1)

  private case class FastqInfo(
    readCount: Long,
    pairedReads: Boolean
  )

  private object FastqInfo {
    implicit val mon: Monoid[FastqInfo] = new Monoid[FastqInfo] {
      override def empty: FastqInfo = FastqInfo(0L, true)
      override def combine(x: FastqInfo, y: FastqInfo): FastqInfo =
        FastqInfo(x.readCount + y.readCount, x.pairedReads && y.pairedReads)
    }
  }

  private case class FileGraph(
    fileToReplicate: Map[String, String],
    fileToSources: Map[String, Set[String]],
    fastqInfos: Map[String, FastqInfo],
    allFiles: Set[String]
  )

  private object FileGraph {
    implicit val mon: Monoid[FileGraph] = new Monoid[FileGraph] {
      override def empty: FileGraph =
        FileGraph(Map.empty, Map.empty, Map.empty, Set.empty)
      override def combine(x: FileGraph, y: FileGraph): FileGraph =
        FileGraph(
          x.fileToReplicate |+| y.fileToReplicate,
          x.fileToSources |+| y.fileToSources,
          x.fastqInfos |+| y.fastqInfos,
          x.allFiles |+| y.allFiles
        )
    }
  }
}
