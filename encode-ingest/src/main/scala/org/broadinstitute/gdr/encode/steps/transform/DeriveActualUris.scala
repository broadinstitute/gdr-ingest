package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.Effect
import cats.implicits._
import fs2.{Scheduler, Stream}
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.client.EncodeClient
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class DeriveActualUris(in: File, override protected val out: File)(
  implicit ec: ExecutionContext,
  s: Scheduler
) extends IngestStep {

  override def process[F[_]: Effect]: Stream[F, Unit] =
    EncodeClient
      .stream[F]
      .flatMap { client =>
        IngestStep
          .readJsonArray(in)
          .mapAsyncUnordered(EncodeClient.Parallelism)(deriveUri(client))
      }
      .to(IngestStep.writeJsonArray(out))

  private def deriveUri[F[_]: Effect](
    client: EncodeClient[F]
  )(file: JsonObject): F[JsonObject] = {
    val shouldDerive = file(JoinReplicatesToFiles.FileAvailableField)
      .flatMap(_.asBoolean)
      .getOrElse(false)

    val withDerived = if (!shouldDerive) {
      Effect[F].pure(file)
    } else {
      Effect[F]
        .fromOption(
          file(ShapeFileMetadata.FileHrefField).flatMap(_.asString),
          new IllegalStateException(
            s"File metadata $file has no ${ShapeFileMetadata.FileHrefField} field"
          )
        )
        .flatMap(client.deriveDownloadUri)
        .map { uri =>
          file
            .add(DeriveActualUris.DownloadUriField, uri.toString().asJson)
        }
    }

    withDerived.map(_.remove(ShapeFileMetadata.FileHrefField))
  }
}

object DeriveActualUris {
  val DownloadUriField = "download_uri"
}
