package org.broadinstitute.gdr.encode.steps

import better.files.File
import cats.effect.{Effect, Sync}
import cats.syntax.either._
import fs2.Stream
import io.circe.Json
import org.apache.commons.codec.binary.{Base64, Hex}
import org.broadinstitute.gdr.encode.client.EncodeClient
import org.http4s.Uri

import scala.language.higherKinds

class BuildUrlManifest(fileMetadata: File, tsvOut: File, parallelism: Int)
    extends IngestStep {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def run[F[_]: Effect]: F[Unit] = {
    val clientStream = EncodeClient.stream[F]

    val manifestRows = clientStream.flatMap { client =>
      readFileMetadata
        .mapAsyncUnordered(parallelism)(buildFileRow(client))
        .flatMap { row =>
          Stream
            .fromIterator(s"${row.uri}\t${row.size}\t${row.md5}\n".getBytes.iterator)
        }
    }

    Stream
      .fromIterator(s"${BuildUrlManifest.ManifestHeader}\n".getBytes.iterator)
      .append(manifestRows)
      .to(fs2.io.file.writeAll(tsvOut.path))
      .compile
      .drain
  }

  private def readFileMetadata[F[_]: Sync]: Stream[F, Json] =
    fs2.io.file
      .readAll(fileMetadata.path, 8192)
      .through(io.circe.fs2.byteArrayParser)

  private def buildFileRow[F[_]: Effect](
    client: EncodeClient[F]
  )(metadata: Json): F[BuildUrlManifest.ManifestRow] = {
    val E = Effect[F]
    val cursor = metadata.hcursor

    val fileInfo = for {
      downloadEndpoint <- cursor.get[String]("href")
      size <- cursor.get[Long]("file_size")
      hexMd5 <- cursor.get[String]("md5sum")
      md5Bytes <- Either.catchNonFatal(Hex.decodeHex(hexMd5))
    } yield {
      (downloadEndpoint, size, Base64.encodeBase64String(md5Bytes))
    }

    E.flatMap(E.fromEither(fileInfo)) {
      case (downloadEndpoint, size, md5) =>
        E.map(client.deriveDownloadUrl(downloadEndpoint)) { realUrl =>
          BuildUrlManifest.ManifestRow(realUrl, size, md5)
        }
    }
  }
}

object BuildUrlManifest {
  val ManifestHeader = "TsvHttpData-1.0"

  case class ManifestRow(uri: Uri, size: Long, md5: String)
}
