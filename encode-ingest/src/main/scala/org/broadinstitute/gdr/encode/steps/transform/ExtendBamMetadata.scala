package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import cats.kernel.Monoid
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class ExtendBamMetadata(in: File, override protected val out: File) extends IngestStep {
  import ExtendBamMetadata._

  override def process[F[_]: Effect]: Stream[F, Unit] =
    fileGraph.flatMap { graph =>
      IngestStep.readJsonArray(in).map(addFieldsIfBam(_, graph))
    }.unNone.to(IngestStep.writeJsonArray(out))

  private def fileGraph[F[_]: Sync]: Stream[F, FileGraph] =
    IngestStep
      .readJsonArray(in)
      .evalMap { file =>
        val newInfo = for {
          id <- file("@id").flatMap(_.asString)
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

          val updatedReplicates =
            replicateRef.fold(Map.empty[String, String])(r => Map(id -> r))
          val updatedSources =
            sourceFiles.fold(Map.empty[String, Vector[String]])(s => Map(id -> s))
          val updatedCounts = fastqInfo
            .filter(_ => fileType.equals("fastq"))
            .fold(Map.empty[String, FastqInfo])(i => Map(id -> i))

          (updatedReplicates, updatedSources, updatedCounts)
        }
        Sync[F].fromOption(
          newInfo,
          new IllegalStateException(s"Expected fields not found in $file")
        )
      }
      .foldMonoid
      .map(FileGraph.tupled)

  private def addFieldsIfBam(file: JsonObject, graph: FileGraph): Option[JsonObject] = {
    for {
      id <- file("@id").flatMap(_.asString)
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
    } yield {
      val extraFields =
        if (fileType == "bam") bamFields(file, fastqInfo) else Map.empty

      JsonObject
        .fromMap(extraFields ++ file.toMap)
        .add(
          MergeFilesMetadata
            .joinedName(MergeFilesMetadata.ReplicatePrefix, ReplicateRefsPrefix),
          nonEmptyIds.asJson
        )
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
        PercentAlignedField -> (aligned / total).asJson,
        PercentDupsField -> (duplicated / total).asJson
      )
    }

    Iterable
      .concat(
        Iterable(
          ReadCountField -> fastqInfo.readCount.asJson,
          RunTypeField -> (if (fastqInfo.pairedReads) "paired" else "unpaired").asJson
        ),
        bamJson("mapped_read_length").map(ReadLengthField -> _),
        qcFields.getOrElse(Iterable.empty)
      )
      .toMap
  }
}

object ExtendBamMetadata {
  val PercentDupsField = "percent_duplicated"
  val PercentAlignedField = "percent_aligned"
  val ReadCountField = "read_count"
  val ReadLengthField = "read_length"
  val RunTypeField = "run_type"

  val ReplicateRefsPrefix = "file"

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
    fileToSources: Map[String, Seq[String]],
    fastqInfos: Map[String, FastqInfo]
  )
}
