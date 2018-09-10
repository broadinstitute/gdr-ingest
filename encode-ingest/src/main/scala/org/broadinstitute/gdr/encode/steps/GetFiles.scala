package org.broadinstitute.gdr.encode.steps

import better.files.File
import cats.effect.{Effect, Sync}
import fs2.Stream
import org.broadinstitute.gdr.encode.client.EncodeClient

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GetFiles(in: File, out: File)(implicit ec: ExecutionContext) extends IngestStep {
  override def run[F[_]: Effect]: F[Unit] = {
    val metadataStream = EncodeClient.stream[F].flatMap { client =>
      fileRefs.mapAsyncUnordered(EncodeClient.Parallelism)(client.get)
    }

    val byteStream = metadataStream.map(_.noSpaces).intersperse(",").flatMap { str =>
      Stream.emits(str.getBytes)
    }

    Stream
      .emit('['.toByte)
      .append(byteStream)
      .append(Stream.emit(']'.toByte))
      .to(fs2.io.file.writeAll(out.path))
      .compile
      .drain
  }

  private def fileRefs[F[_]: Sync]: Stream[F, String] =
    fs2.io.file
      .readAll(in.path, 8192)
      .through(io.circe.fs2.byteArrayParser)
      .flatMap { json =>
        json.hcursor
          .get[Seq[String]]("files")
          .fold(
            Stream.raiseError,
            Stream.emits
          )
          .covary[F]
      }

}
