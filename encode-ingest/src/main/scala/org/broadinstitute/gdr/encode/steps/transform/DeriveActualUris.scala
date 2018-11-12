package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{ConcurrentEffect, ContextShift, Effect, Timer}
import cats.syntax.all._
import fs2.Stream
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.client.EncodeClient
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class DeriveActualUris(in: File, override protected val out: File, ec: ExecutionContext)
    extends IngestStep {

  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] =
    EncodeClient
      .stream[F]
      .flatMap { client =>
        IngestStep
          .readJsonArray(ec)(in)
          .mapAsyncUnordered(EncodeClient.Parallelism)(deriveUri(client))
      }
      .to(IngestStep.writeJsonArray(ec)(out))

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
