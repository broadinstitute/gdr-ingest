package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.Effect
import cats.implicits._
import io.circe.Json
import io.circe.literal._
import org.broadinstitute.gdr.encode.client.EncodeClient
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class DeriveActualUris(in: File, out: File)(implicit ec: ExecutionContext)
    extends IngestStep {

  override def run[F[_]: Effect]: F[Unit] =
    EncodeClient
      .stream[F]
      .flatMap { client =>
        IngestStep
          .readJsonArray(in)
          .mapAsyncUnordered(EncodeClient.Parallelism)(deriveUri(client))
      }
      .to(IngestStep.writeJsonArray(out))
      .compile
      .drain

  private def deriveUri[F[_]: Effect](client: EncodeClient[F])(file: Json): F[Json] =
    Effect[F]
      .fromEither(file.hcursor.get[String]("href"))
      .flatMap(client.deriveDownloadUri)
      .map { uri =>
        file.deepMerge(json"""{ ${DeriveActualUris.ActualUriName}: ${uri.toString()} }""")
      }
}

object DeriveActualUris {
  val ActualUriName = "download_uri"
}
