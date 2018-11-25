package org.broadinstitute.gdr.encode.explorer

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

object EncodeExplorer extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    loadConfigF[IO, ExplorerConfig]("org.broadinstitute.gdr.encode.explorer")
      .flatMap(run)

  private def run(config: ExplorerConfig): IO[ExitCode] =
    DbClient.resource[IO](config.db).use { db =>
      val facetsController = new FacetsController(config.fields, db)

      // Fail fast on bad config, and also warm up the DB connection pool.
      facetsController.validateFields.flatMap { _ =>
        implicit val filterQueryDecoder: QueryParamDecoder[Map[String, Vector[String]]] =
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
                  Map(k -> Vector(v))
                }
              }
            }
          }

        object FilterQueryDecoder
            extends OptionalQueryParamDecoderMatcher[
              Map[String, Vector[String]]
            ]("filter")

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
