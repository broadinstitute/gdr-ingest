package org.broadinstitute.gdr.encode.steps

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.Json
import org.broadinstitute.gdr.encode.client.EncodeClient

import scala.language.higherKinds

class SearchFileMetadata(in: File, out: File) extends IngestStep {
  override def run[F[_]: Effect]: F[Unit] = {
    val metadataStream = for {
      searchParams <- buildExperimentBatches[F]
      client <- EncodeClient.stream[F]
      metadata <- runSearch(client)(searchParams)
    } yield {
      metadata
    }

    val byteStream = metadataStream.map(_.noSpaces).intersperse(",").flatMap { str =>
      Stream.fromIterator(str.getBytes.iterator)
    }

    Stream
      .emit('['.toByte)
      .append(byteStream)
      .append(Stream.emit(']'.toByte))
      .to(fs2.io.file.writeAll(out.path))
      .compile
      .drain
  }

  private def buildExperimentBatches[F[_]: Sync]: Stream[F, Seq[String]] =
    fs2.io.file
      .readAll(in.path, 8192)
      .through(fs2.text.utf8Decode)
      .through(fs2.text.lines)
      .zipWithIndex
      .groupAdjacentBy { case (_, i) => i / 100 }
      .map {
        case (_, idSegment) =>
          idSegment
            .fold(Seq.empty[String]) { case (acc, (id, _)) => id +: acc }
            .force
            .run
            ._2
      }

  private def runSearch[F[_]: Effect](
    client: EncodeClient[F]
  )(experimentBatch: Seq[String]): Stream[F, Json] = {
    val experimentParams = experimentBatch.map("dataset" -> _)
    client.search(
      Seq.concat(
        experimentParams,
        Seq(
          // Only pull publicly-available data.
          "status" -> "released",
          // Pull everything.
          "limit" -> "all",
          // Make sure search returns properly-formatted data.
          "frame" -> "object",
          "format" -> "json",
          // Make sure to only get files.
          "type" -> "File",
          // Pull bams...
          "file_format" -> "bam",
          "output_type" -> "unfiltered alignments",
          // And bigWigs...
          "file_format" -> "bigWig",
          "output_type" -> "fold change over control",
          // and bigBeds.
          "file_format" -> "bigBed",
          "output_type" -> "peaks"
        )
      ): _*
    )
  }

}
