package org.broadinstitute.gdr.encode.explorer

import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import io.circe.syntax._
import org.broadinstitute.gdr.encode.explorer.dataset.DatasetController
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.facets.FacetsController
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import pureconfig.module.catseffect._

/**
  * Top-level entry point for the API server.
  *
  * Manages everything related to HTTP for now, but it might be good
  * to split the route definitions out into a separate class.
  */
object EncodeExplorer extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    loadConfigF[IO, ExplorerConfig]("org.broadinstitute.gdr.encode.explorer")
      .flatMap(run)

  /**
    * Run the server using the given config.
    *
    * Only returns when an error occurs prior to binding the HTTP handler.
    */
  private def run(config: ExplorerConfig): IO[ExitCode] =
    DbClient.resource[IO](config.db).use {
      /*
       * NOTE: 'db' gets shut down after all the code in this block completes,
       * so all business logic requiring DB acccess has to run in this scope.
       *
       * If this block gets too hairy, we can refactor to wrap up components
       * like the `FacetsController` into DB => Controller functions defined
       * elsewhere, and then thread the dependencies through here.
       */
      db =>
        val facetsController = new FacetsController(config.fields, db)

        // Fail fast on bad config, and also warm up the DB connection pool.
        // TODO: Experiment with this a repeated query param instead of an all-in-one.
        facetsController.validateFields.flatMap { _ =>
          implicit val filterQueryDecoder: QueryParamDecoder[FacetsController.Filters] =
            QueryParamDecoder[String].map { s =>
              if (s.isEmpty) {
                Map.empty
              } else {
                s.split('|').toList.foldMap { kv =>
                  val i = kv.indexOf('=')
                  if (i < 0) {
                    throw new IllegalArgumentException(s"Bad filter: $kv")
                  } else {
                    val (k, v) = (kv.take(i), kv.drop(i + 1))
                    Map(k -> NonEmptyList.of(v))
                  }
                }
              }
            }

          // Ugly as an object, but http4s has somehow designed their syntax
          // to make it not work as a val.
          object FilterQueryDecoder
              extends OptionalQueryParamDecoderMatcher[FacetsController.Filters]("filter")

          val routes = HttpRoutes.of[IO] {
            case GET -> Root / "api" / "dataset" =>
              Ok(DatasetController.default.datasetInfo.asJson)
            case GET -> Root / "api" / "facets" :? FilterQueryDecoder(filters) =>
              Ok(facetsController.getFacets(filters.getOrElse(Map.empty)).map(_.asJson))
          }

          val app = if (config.logging.logHeaders || config.logging.logBodies) {
            Logger[IO](
              logHeaders = config.logging.logHeaders,
              logBody = config.logging.logBodies
            )(routes.orNotFound)
          } else {
            routes.orNotFound
          }

          // NOTE: .serve returns a never-ending stream, so this will only
          // complete on SIGSTOP or SIGINT.
          BlazeServerBuilder[IO]
            .bindHttp(config.port, "0.0.0.0")
            .withHttpApp(app)
            .serve
            .compile
            .lastOrError
        }
    }
}
