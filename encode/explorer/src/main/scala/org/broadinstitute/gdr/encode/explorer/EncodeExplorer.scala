package org.broadinstitute.gdr.encode.explorer

import cats.data.Validated.{Invalid, Valid}
import cats.effect.{ExitCode, IO, IOApp}
import io.circe.{Decoder, DecodingFailure}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.explorer.dataset.DatasetController
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.export.{ExportController, ExportRequest}
import org.broadinstitute.gdr.encode.explorer.facets.FacetsController
import org.broadinstitute.gdr.encode.explorer.fields.FieldFilter
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig, GZip, Logger}
import pureconfig.module.catseffect._

import scala.concurrent.duration._

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
        val exportController = new ExportController(config.export, db)

        // Fail fast on bad config, and also warm up the DB connection pool.
        // TODO: Experiment with this a repeated query param instead of an all-in-one.
        facetsController.validateFields.flatMap { _ =>
          implicit val filterQueryDecoder: QueryParamDecoder[FieldFilter.Filters] =
            queryParam =>
              FieldFilter
                .parseFilters(queryParam.value.split('|').toList, config.fields)
                .leftMap(_.map(err => ParseFailure(err, err)))

          implicit val exportBodyDecoder: Decoder[ExportRequest] = cursor =>
            for {
              cohort <- cursor.get[Option[String]]("cohortName")
              encodedFilters <- cursor.get[List[String]]("filter")
              decodedFilters <- FieldFilter
                .parseFilters(encodedFilters, config.fields)
                .leftMap(errs => DecodingFailure(errs.head, Nil))
                .toEither
            } yield {
              ExportRequest(cohort, decodedFilters)
            }

          // Ugly as an object, but http4s has somehow designed their syntax
          // to make it not work as a val.
          object FilterQueryDecoder
              extends OptionalValidatingQueryParamDecoderMatcher[
                FieldFilter.Filters
              ]("filter")

          object CohortQueryDecoder
              extends OptionalQueryParamDecoderMatcher[String]("cohortName")

          val routes = HttpRoutes.of[IO] {
            case GET -> Root / "api" / "dataset" =>
              Ok(DatasetController.default.datasetInfo.asJson)

            case GET -> Root / "api" / "facets" :? FilterQueryDecoder(maybeFilters) =>
              maybeFilters match {
                case None =>
                  Ok(facetsController.getFacets(Map.empty).map(_.asJson))
                case Some(Invalid(errs)) =>
                  BadRequest(errs.map(_.sanitized).asJson)
                case Some(Valid(filters)) =>
                  Ok(facetsController.getFacets(filters).map(_.asJson))
              }

            case req @ POST -> Root / "api" / "exportUrl" =>
              for {
                exportReq <- req.as[ExportRequest]
                body <- exportController.exportUrl(exportReq)
                response <- Ok(body.asJson)
              } yield response

            case GET -> Root / "api" / "export"
                  :? FilterQueryDecoder(maybeFilters)
                    +& CohortQueryDecoder(maybeCohort) =>
              maybeFilters match {
                case None =>
                  Ok(exportController.export(ExportRequest(maybeCohort, Map.empty)))
                case Some(Invalid(errs)) =>
                  BadRequest(errs.map(_.sanitized).asJson)
                case Some(Valid(filters)) =>
                  Ok(exportController.export(ExportRequest(maybeCohort, filters)))
              }
          }

          /*
           * Add "middleware" to the routes to:
           *   1. Log requests and responses
           *   2. gzip responses
           *   3. Allow CORS from Terra
           */
          val app = {
            val corsConfig = CORSConfig(
              anyOrigin = false,
              allowCredentials = true,
              maxAge = 1.hour.toSeconds,
              anyMethod = false,
              allowedOrigins = Set("https://app.terra.bio"),
              allowedMethods = Some(Set("GET"))
            )
            val withLogging =
              Logger[IO](logHeaders = true, logBody = true)(routes.orNotFound)

            CORS(GZip(withLogging), corsConfig)
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
