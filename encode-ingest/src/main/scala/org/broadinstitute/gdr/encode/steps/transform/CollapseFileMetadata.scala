package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.Json
import io.circe.literal._
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class CollapseFileMetadata(in: File, out: File) extends IngestStep {
  import CollapseFileMetadata._

  private val logger = org.log4s.getLogger

  override def run[F[_]: Effect]: F[Unit] =
    fileGraph.flatMap { graph =>
      fileStream
        .filter(isLeaf)
        .evalMap(traceFiles(_, graph))
    }.unNone
      .to(IngestStep.writeJsonArray(out))
      .compile
      .drain

  private def fileStream[F[_]: Sync]: Stream[F, Json] =
    fs2.io.file
      .readAll(in.path, 8192)
      .through(io.circe.fs2.byteArrayParser)

  private def fileGraph[F[_]: Sync]: Stream[F, FileGraph] =
    fileStream.evalMap { file =>
      val cursor = file.hcursor
      val newInfo = for {
        id <- cursor.get[String]("@id")
        replicateRef <- cursor.get[Option[String]]("replicate")
        sourceFiles <- cursor.get[Option[List[String]]]("derived_from")
        fileType <- cursor.get[String]("file_format")
        readCounts <- cursor.get[Option[Long]]("read_count")
      } yield {
        val updatedReplicates =
          replicateRef.fold(Map.empty[String, String])(r => Map(id -> r))
        val updatedSources =
          sourceFiles.fold(Map.empty[String, List[String]])(s => Map(id -> s))
        val updatedCounts = readCounts
          .filter(_ => fileType.equals("fastq"))
          .fold(Map.empty[String, Long])(c => Map(id -> c))

        (updatedReplicates, updatedSources, updatedCounts)
      }
      Sync[F].fromEither(newInfo)
    }.foldMonoid.map(FileGraph.tupled)

  private def isLeaf(file: Json): Boolean = {
    val cursor = file.hcursor
    val keepFile = for {
      status <- cursor.get[String]("status")
      format <- cursor.get[String]("file_format")
      typ <- cursor.get[String]("output_type")
    } yield {
      status.equals("released") && FormatTypeWhitelist.contains(format -> typ)
    }

    keepFile.getOrElse(false)
  }

  private def traceFiles[F[_]: Sync](file: Json, graph: FileGraph): F[Option[Json]] = {
    @scala.annotation.tailrec
    def exploreGraph(
      id: String,
      nextIds: List[String],
      replicateAcc: Set[String],
      readCount: Long
    ): (Set[String], Long) = {

      val newNext = graph.fileToSources
        .get(id)
        .fold(nextIds)(nextIds ++ _)

      val newReplicates = graph.fileToReplicate
        .get(id)
        .fold(replicateAcc)(replicateAcc + _)

      val newReads = readCount + graph.fastqReadCounts.getOrElse(id, 0L)

      newNext match {
        case Nil          => (newReplicates, newReads)
        case next :: more => exploreGraph(next, more, newReplicates, newReads)
      }
    }

    val cursor = file.hcursor

    Sync[F].fromEither(cursor.get[String]("@id")).flatMap { id =>
      val (replicateIds, totalReads) = exploreGraph(id, Nil, Set.empty, 0L)
      if (replicateIds.isEmpty) {
        Sync[F].delay {
          logger.warn(s"Dropping file $id, no replicates found")
          None
        }
      } else {
        val withDerived = Sync[F].fromEither {
          cursor.get[String]("file_type").map { fileType =>
            val base = json"""{ $ReplicateRefsField: $replicateIds }"""

            file.deepMerge {
              if (fileType == "bam") {
                base.deepMerge(json"""{ $ReadCountField: $totalReads }""")
              } else {
                base
              }
            }
          }
        }

        withDerived.map(Some(_))
      }
    }
  }
}

object CollapseFileMetadata {
  val ReadCountField = "read_count"
  val ReplicateRefsField = "replicate_refs"

  val FormatTypeWhitelist = Set(
    "bam" -> "unfiltered alignments",
    "bigBed" -> "peaks",
    "bigWig" -> "fold change over control"
  )

  private case class FileGraph(
    fileToReplicate: Map[String, String],
    fileToSources: Map[String, Seq[String]],
    fastqReadCounts: Map[String, Long]
  )
}
