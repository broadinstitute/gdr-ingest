package org.broadinstitute.gdr.encode.client

import cats.effect.Effect
import fs2.Stream
import io.circe.Json
import org.http4s.{Method, Query, Request, Uri}
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.http4s.headers.Location

import scala.language.higherKinds

class EncodeClient[F[_]: Effect] private (client: Client[F]) {

  private val E = Effect[F]

  def search(searchParams: (String, String)*): Stream[F, Json] = {

    val searchUri = EncodeClient.EncodeUri
      .withPath("/search/")
      .copy(query = Query.fromPairs(searchParams: _*))

    Stream
      .eval(
        client.expect[Json](Request[F](uri = searchUri))(org.http4s.circe.jsonDecoder)
      )
      .flatMap { res =>
        res.hcursor
          .downField("@graph")
          .as[Seq[Json]]
          .fold(Stream.raiseError[Json], jss => Stream.fromIterator(jss.iterator))
      }
  }

  def deriveDownloadUrl(downloadEndpoint: String): F[Uri] = {

    val request = Request[F](
      method = Method.HEAD,
      uri = EncodeClient.EncodeUri.withPath(downloadEndpoint)
    )
    client.fetch(request) { response =>
      response.headers
        .get(Location)
        .fold(
          E.raiseError[Uri](
            new IllegalStateException(
              s"HEAD of $downloadEndpoint returned no redirect URI"
            )
          )
        ) { redirectLocation =>
          // Redirects look like:
          //  https://download.encodeproject.org/https://encode-files.s3.amazonaws.com/2016/10/14/a0ef19e5-d9d6-4984-b29d-47a64abf4d0d/ENCFF398VEH.bam?key=value&key2=value2
          // Google needs the embedded S3 uri:   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
          E.fromEither(Uri.fromString(redirectLocation.uri.path.dropWhile(_ == '/')))
        }
    }
  }
}

object EncodeClient {
  private val EncodeUri = Uri.unsafeFromString("https://www.encodeproject.org")

  def stream[F[_]: Effect]: Stream[F, EncodeClient[F]] =
    Http1Client.stream().map(new EncodeClient[F](_))
}
