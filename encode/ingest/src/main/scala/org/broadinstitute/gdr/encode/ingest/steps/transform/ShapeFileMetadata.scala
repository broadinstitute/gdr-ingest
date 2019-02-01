package org.broadinstitute.gdr.encode.ingest.steps.transform

import better.files.File
import cats.effect._
import cats.implicits._
import cats.kernel.Monoid
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.ingest.EncodeFields
import org.broadinstitute.gdr.encode.ingest.client.EncodeClient
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class ShapeFileMetadata(
  fileMetadata: File,
  override protected val out: File,
  ec: ExecutionContext
) extends IngestStep {
  import EncodeFields._
  import ShapeFileMetadata._

  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] =
    fileGraph.flatMap { graph =>
      IngestStep
        .readJsonArray(ec)(fileMetadata)
        .map(f => f("status").flatMap(_.asString).map(_ -> f))
        .collect {
          case Some(("released", f)) => addFields(graph)(f)
        }
    }.unNone
      .through(IngestStep.renameFields(FieldsToRename))
      .map(_.filterKeys(RetainedFields.contains))
      .through(IngestStep.writeJsonArray(ec)(out))

  private def fileGraph[F[_]: Sync: ContextShift]: Stream[F, FileGraph] =
    IngestStep
      .readJsonArray(ec)(fileMetadata)
      .evalMap { file =>
        val newInfo = for {
          id <- file(EncodeIdField).flatMap(_.asString)
          fileType <- file("file_format").flatMap(_.asString)
        } yield {
          val replicateRef = file("replicate").flatMap(_.asString)
          val sourceFiles = file("derived_from").flatMap(_.as[Set[String]].toOption)

          FileGraph(
            replicateRef.fold(Map.empty[String, String])(r => Map(id -> r)),
            sourceFiles.fold(Map.empty[String, Set[String]])(s => Map(id -> s)),
            if (fileType == "fastq") {
              val readCount = file("read_count")
                .flatMap(_.as[Long].toOption)
                .getOrElse(0L)
              val pairedRun = file("run_type")
                .flatMap(_.asString)
                .forall(_ == "paired-ended")
              Map(id -> FastqInfo(readCount, pairedRun))
            } else {
              Map.empty
            },
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
      accession <- file("accession").flatMap(_.asString)
      (replicateIds, fastqInfo) = exploreGraph(
        id,
        graph,
        Nil,
        Set.empty,
        FastqInfo.mon.empty,
        Set.empty
      )
      nonEmptyIds <- ensureReplicates(id, replicateIds)
      fileFormat <- file("file_format").flatMap(_.asString)
      sourceFiles <- file("derived_from")
        .flatMap(_.as[Set[String]].toOption)
        .map(_.map(ShapeFileMetadata.extractFileId))
    } yield {
      val sourceFilesFromExperiments = sourceFiles
        .intersect(graph.allFiles)
      val sourceReferences = sourceFiles.diff(sourceFilesFromExperiments)

      val generalFields = Map(
        DataSourceField -> "ENCODE".asJson,
        DerivedFromExperimentField -> sourceFilesFromExperiments.asJson,
        DerivedFromReferenceField -> sourceReferences.asJson,
        FileIdField -> accession.asJson,
        EncodeLinkField -> (EncodeClient.EncodeUri / accession).toString.asJson,
        ReplicateFkField -> nonEmptyIds.asJson
      )

      val typeSpecificFields = fileFormat match {
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

  val AssemblyField = "reference_genome_assembly"
  val CloudMetadataField = "cloud_metadata"
  val DataSourceField = "data_source"
  val DateCreatedField = "date_file_created"
  val DerivedFromExperimentField = "derived_from_exp"
  val DerivedFromReferenceField = "derived_from_ref"
  val EncodeLinkField = "more_info"
  val FileFormatField = "file_format"
  val FileIdField = "file_id"
  val FileMd5Field = "md5sum"
  val FileSizeField = "file_size"
  val FileTypeField = "file_format_subtype"
  val OutputTypeField = "data_type"
  val PercentDupsField = "percent_duplicated_reads"
  val PercentAlignedField = "percent_aligned_reads"
  val ReadCountField = "read_count"
  val ReadLengthField = "read_length"
  val RunTypeField = "paired_end_sequencing"

  val ReplicateRefsPrefix = "file"

  val ReplicateFkField =
    EncodeFields.joinedName("id", JoinReplicateMetadata.ReplicatePrefix)

  val FieldsToRename = Map(
    "assembly" -> AssemblyField,
    "date_created" -> DateCreatedField,
    "file_type" -> FileTypeField,
    "output_type" -> OutputTypeField
  )

  val RetainedFields = Set(
    CloudMetadataField,
    DataSourceField,
    DerivedFromExperimentField,
    DerivedFromReferenceField,
    EncodeFields.EncodeIdField,
    EncodeLinkField,
    FileIdField,
    FileFormatField,
    FileSizeField,
    FileMd5Field,
    PercentAlignedField,
    PercentDupsField,
    ReadCountField,
    ReadLengthField,
    ReplicateFkField,
    RunTypeField
  ) ++ FieldsToRename.values

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
