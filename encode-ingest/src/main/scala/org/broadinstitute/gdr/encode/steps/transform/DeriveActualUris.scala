package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.Effect
import cats.implicits._
import fs2.Stream
import io.circe.Json
import io.circe.literal._
import org.broadinstitute.gdr.encode.client.EncodeClient
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class DeriveActualUris(in: File, override protected val out: File)(
  implicit ec: ExecutionContext
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

  private def deriveUri[F[_]: Effect](client: EncodeClient[F])(file: Json): F[Json] =
    Effect[F]
      .fromEither(file.hcursor.get[String]("href"))
      .flatMap(client.deriveDownloadUri)
      .map { uri =>
        file.deepMerge(
          json"""{ ${DeriveActualUris.DownloadUriField}: ${uri.toString()} }"""
        )
      }
}

object DeriveActualUris {
  val DownloadUriField = "download_uri"
}
