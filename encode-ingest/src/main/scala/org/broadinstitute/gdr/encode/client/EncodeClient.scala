package org.broadinstitute.gdr.encode.client

import java.util.concurrent.Executors

import cats.effect._
import cats.syntax.all._
import fs2.Stream
import io.circe.{Json, JsonObject}
import org.http4s.{Method, Query, Request, Status, Uri}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.{Logger, Retry, RetryPolicy}
import org.http4s.headers.Location

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.higherKinds

/**
  * Client for pulling information out of ENCODE's REST API.
  *
  * @param client generic blaze/http4s client handling the actual request-sending/parsing logic
  * @see https://www.encodeproject.org/help/rest-api/
  */
class EncodeClient[F[_]: Effect] private (client: Client[F]) {

  /**
    * Typeclass instance with knowledge of how to capture side-effects within the wrapper type `F`.
    */
  private val E = Effect[F]

  /**
    * Pull all metadata for a specific ENCODE entity type matching a set of criteria.
    *
    * @param entityType category to search within. Must be one of the schema titles listed at https://www.encodeproject.org/profiles/
    * @param searchParams key-value pairs to match entities against. If a key is provided multiple times, entities
    *                     matching any of the associated values will be returned
    */
  def search(
    entityType: String,
    searchParams: Seq[(String, String)]
  ): Stream[F, JsonObject] = {

    val allParams = Seq("type" -> entityType, "limit" -> "all", "format" -> "json") ++ searchParams
    val searchUri = EncodeClient.EncodeUri
      .withPath("/search/")
      .copy(query = Query.fromPairs(allParams: _*))

    val request = client.expectOr[Json](Request[F](uri = searchUri)) { failedResponse =>
      E.pure(failedResponse.status).map { code =>
        if (code == Status.NotFound) {
          EncodeClient.NoResultsFound
        } else {
          UnexpectedStatus(code)
        }
      }
    }(org.http4s.circe.jsonDecoder)

    Stream
      .eval(request)
      .flatMap { res =>
        res.hcursor
          .downField("@graph")
          .as[Seq[JsonObject]]
          .fold(Stream.raiseError[F], jss => Stream.emits(jss))
      }
      .recoverWith {
        case EncodeClient.NoResultsFound => Stream.empty
      }
  }

  /**
    * Query ENCODE for the AWS download URI backing the download endpoint for a file entity.
    *
    * @param downloadEndpoint value of an "href" field pulled from file entity metadata
    */
  def deriveDownloadUri(downloadEndpoint: String): F[Uri] = {

    val request = Request[F](
      method = Method.HEAD,
      uri = EncodeClient.EncodeUri.withPath(downloadEndpoint)
    )
    client.fetch(request) { response =>
      response.headers
        .get(Location)
        .fold[F[Uri]](
          E.raiseError(
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

  /**
    * Max number of requests a client should send to ENCODE at a time.
    *
    * FIXME: This should really be something the client enforces internally.
    */
  val Parallelism: Int = Runtime.getRuntime.availableProcessors()

  val EncodeUri: Uri = Uri.unsafeFromString("https://www.encodeproject.org")

  def stream[F[_]: ConcurrentEffect: Timer]: Stream[F, EncodeClient[F]] =
    for {
      ec <- Stream.resource(schedulerContext[F])
      blaze <- BlazeClientBuilder[F](ec).stream
    } yield {
      val retryPolicy = RetryPolicy[F](RetryPolicy.exponentialBackoff(1.second, 5))
      val wrappedBlaze =
        Retry(retryPolicy)(Logger(logHeaders = true, logBody = false)(blaze))
      new EncodeClient[F](wrappedBlaze)
    }

  private def schedulerContext[F[_]](implicit F: Sync[F]) =
    Resource[F, ExecutionContext](F.delay {
      val executor = Executors.newFixedThreadPool(1)
      val ec = ExecutionContext.fromExecutor(executor)
      (ec, F.delay(executor.shutdown()))
    })

  private object NoResultsFound extends Throwable
}
