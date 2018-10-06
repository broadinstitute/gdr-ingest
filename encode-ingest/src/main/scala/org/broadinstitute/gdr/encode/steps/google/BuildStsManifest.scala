package org.broadinstitute.gdr.encode.steps.google

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.JsonObject
import org.apache.commons.codec.binary.{Base64, Hex}
import org.broadinstitute.gdr.encode.steps.IngestStep
import org.broadinstitute.gdr.encode.steps.transform.JoinReplicatesToFiles

import scala.language.higherKinds

class BuildStsManifest(fileMetadata: File, override protected val out: File)
    extends IngestStep {
  import org.broadinstitute.gdr.encode.EncodeFields._

  override def process[F[_]: Effect]: Stream[F, Unit] = {

    val manifestRows = IngestStep
      .readJsonArray(fileMetadata)
      .filter { file =>
        file(JoinReplicatesToFiles.FileAvailableField)
          .flatMap(_.asBoolean)
          .getOrElse(false)
      }
      .evalMap(buildFileRow[F])

    Stream
      .emit("TsvHttpData-1.0")
      .append(manifestRows)
      .to(IngestStep.writeLines(out))
  }

  private def buildFileRow[F[_]: Sync](metadata: JsonObject): F[String] = {
    val fileRow = for {
      downloadEndpoint <- metadata(DownloadUriField).flatMap(_.asString)
      size <- metadata("file_size").flatMap(_.asNumber).flatMap(_.toLong)
      hexMd5 <- metadata("md5sum").flatMap(_.asString)
      md5Bytes <- Either.catchNonFatal(Hex.decodeHex(hexMd5)).toOption
    } yield {
      s"$downloadEndpoint\t$size\t${Base64.encodeBase64String(md5Bytes)}"
    }

    Sync[F].fromOption(
      fileRow,
      new IllegalStateException(s"Expected fields not found in $metadata")
    )
  }
}
