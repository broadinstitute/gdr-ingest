package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.Effect
import cats.implicits._
import fs2.{Scheduler, Stream}
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.EncodeFields
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
  )(file: JsonObject): F[JsonObject] =
    Effect[F]
      .fromOption(
        file("href").flatMap(_.asString),
        new IllegalStateException(s"File metadata $file has no href field")
      )
      .flatMap(client.deriveDownloadUri)
      .map { uri =>
        file
          .add(EncodeFields.DownloadUriField, uri.toString().asJson)
          .remove("href")
      }
}
