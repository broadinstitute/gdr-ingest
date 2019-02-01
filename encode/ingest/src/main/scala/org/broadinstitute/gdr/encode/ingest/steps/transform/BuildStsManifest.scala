package org.broadinstitute.gdr.encode.ingest.steps.transform

import better.files.File
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.circe.{Decoder, JsonObject}
import io.circe.derivation.{deriveDecoder, renaming}
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class BuildStsManifest(
  fileMetadata: File,
  override protected val out: File,
  ec: ExecutionContext
) extends IngestStep {
  import BuildStsManifest._

  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] = {

    val manifestRows = IngestStep
      .readJsonArray(ec)(fileMetadata)
      .filter { file =>
        file.contains(ShapeFileMetadata.CloudMetadataField) && shouldTransfer(file)
      }
      .evalMap { file =>
        file(ShapeFileMetadata.CloudMetadataField)
          .flatMap(_.as[CloudMetadata].toOption)
          .liftTo[F](
            new IllegalStateException(s"File-to-transfer has no cloud metadata: $file")
          )
      }
      .map(buildFileRow)

    Stream
      .emit("TsvHttpData-1.0")
      .append(manifestRows)
      .through(IngestStep.writeLines(ec)(out))
  }

  private def shouldTransfer(metadata: JsonObject): Boolean = {
    val keepFile = for {
      format <- metadata(ShapeFileMetadata.FileFormatField).flatMap(_.asString)
      typ <- metadata(ShapeFileMetadata.OutputTypeField).flatMap(_.asString)
    } yield {
      FormatTypeWhitelist.contains(format -> typ)
    }
    keepFile.getOrElse(false)
  }

  private def buildFileRow(metadata: CloudMetadata): String =
    s"${metadata.url}\t${metadata.fileSize}\t${metadata.md5sumBase64}"
}

object BuildStsManifest {
  case class CloudMetadata(url: String, fileSize: Long, md5sumBase64: String)
  implicit val metadataDecoder: Decoder[CloudMetadata] = deriveDecoder(renaming.snakeCase)

  val FormatTypeWhitelist = Set(
    "bam" -> "alignments",
    "bigBed" -> "peaks",
    "bigWig" -> "fold change over control"
  )
}
