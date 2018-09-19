package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class CollapseFileMetadata(in: File, override protected val out: File)
    extends IngestStep {
  import CollapseFileMetadata._

  override def process[F[_]: Effect]: Stream[F, Unit] =
    fileGraph.flatMap { graph =>
      IngestStep.readJsonArray(in).evalMap(traceFiles(_, graph))
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
          val readCount = file("read_count").flatMap(_.asNumber.flatMap(_.toLong))

          val updatedReplicates =
            replicateRef.fold(Map.empty[String, String])(r => Map(id -> r))
          val updatedSources =
            sourceFiles.fold(Map.empty[String, Vector[String]])(s => Map(id -> s))
          val updatedCounts = readCount
            .filter(_ => fileType.equals("fastq"))
            .fold(Map.empty[String, Long])(c => Map(id -> c))

          (updatedReplicates, updatedSources, updatedCounts)
        }
        Sync[F].fromOption(
          newInfo,
          new IllegalStateException(s"Expected fields not found in $file")
        )
      }
      .foldMonoid
      .map(FileGraph.tupled)

  private def traceFiles[F[_]: Sync](
    file: JsonObject,
    graph: FileGraph
  ): F[Option[JsonObject]] = {
    @scala.annotation.tailrec
    def exploreGraph(
      id: String,
      nextIds: List[String],
      replicateAcc: Set[String],
      readCount: Long,
      visited: Set[String]
    ): (Set[String], Long) = {

      val newNext = graph.fileToSources
        .get(id)
        .fold(nextIds)(nextIds ++ _)

      val newReplicates = graph.fileToReplicate
        .get(id)
        .fold(replicateAcc)(replicateAcc + _)

      val newReads = readCount + graph.fastqReadCounts.getOrElse(id, 0L)

      newNext.dropWhile(visited.contains) match {
        case Nil => (newReplicates, newReads)
        case next :: more =>
          exploreGraph(next, more, newReplicates, newReads, visited + id)
      }
    }

    Sync[F]
      .fromOption(
        file("@id").flatMap(_.asString),
        new IllegalStateException(s"File metadata $file has no ID")
      )
      .flatMap { id =>
        val (replicateIds, totalReads) = exploreGraph(id, Nil, Set.empty, 0L, Set.empty)
        if (replicateIds.isEmpty) {
          Sync[F].delay {
            logger.warn(s"Dropping file $id, no replicates found")
            None
          }
        } else {
          val maybeMerged = file("file_type").map { tpe =>
            val base = Map(ReplicateRefsField -> replicateIds.asJson)
            val newFields = if (tpe == "bam".asJson) {
              base + (ReadCountField -> totalReads.asJson)
            } else {
              base
            }

            JsonObject.fromMap(newFields ++ file.toMap)
          }

          Sync[F].pure(maybeMerged)
        }
      }
  }
}

object CollapseFileMetadata {
  val ReadCountField = "read_count"
  val ReplicateRefsField = "replicate_refs"

  private case class FileGraph(
    fileToReplicate: Map[String, String],
    fileToSources: Map[String, Seq[String]],
    fastqReadCounts: Map[String, Long]
  )
}
