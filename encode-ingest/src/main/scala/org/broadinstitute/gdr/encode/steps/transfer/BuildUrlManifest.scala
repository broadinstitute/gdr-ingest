package org.broadinstitute.gdr.encode.steps.transfer

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.Json
import org.apache.commons.codec.binary.{Base64, Hex}
import org.broadinstitute.gdr.encode.steps.IngestStep
import org.broadinstitute.gdr.encode.steps.transform.DeriveActualUris

import scala.language.higherKinds

class BuildUrlManifest(fileMetadata: File, tsvOut: File) extends IngestStep {

  override def run[F[_]: Effect]: F[Unit] = {

    val manifestRows = IngestStep
      .readJsonArray(fileMetadata)
      .evalMap(buildFileRow[F])

    Stream
      .emit("TsvHttpData-1.0")
      .append(manifestRows)
      .intersperse("\n")
      .flatMap(str => Stream.emits(str.getBytes))
      .to(fs2.io.file.writeAll(tsvOut.path))
      .compile
      .drain
  }

  private def buildFileRow[F[_]: Sync](metadata: Json): F[String] = {
    val cursor = metadata.hcursor
    val fileInfo = for {
      downloadEndpoint <- cursor.get[String](DeriveActualUris.ActualUriName)
      size <- cursor.get[Long]("file_size")
      hexMd5 <- cursor.get[String]("md5sum")
      md5Bytes <- Either.catchNonFatal(Hex.decodeHex(hexMd5))
    } yield {
      (downloadEndpoint, size, Base64.encodeBase64String(md5Bytes))
    }

    fileInfo.liftTo[F].map {
      case (uri, size, md5) => s"$uri\t$size\t$md5"
    }
  }
}
