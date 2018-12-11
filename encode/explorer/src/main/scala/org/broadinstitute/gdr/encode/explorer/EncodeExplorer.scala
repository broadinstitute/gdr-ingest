package org.broadinstitute.gdr.encode.explorer

import cats.effect.{ExitCode, IO, IOApp}
import org.broadinstitute.gdr.encode.explorer.count.CountController
import org.broadinstitute.gdr.encode.explorer.dataset.DatasetController
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.export.ExportController
import org.broadinstitute.gdr.encode.explorer.facets.FacetsController
import org.broadinstitute.gdr.encode.explorer.web.ExplorerApi
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
    DbClient.resource[IO, IO.Par](config.db).use {
      /*
       * NOTE: 'db' gets shut down after all the code in this block completes,
       * so all business logic requiring DB acccess has to run in this scope.
       *
       * If this block gets too hairy, we can refactor to wrap up components
       * like the `FacetsController` into DB => Controller functions defined
       * elsewhere, and then thread the dependencies through here.
       */
      db =>
        // Fail fast on bad config, and also warm up the DB connection pool.
        db.validateFields(config.fields).flatMap { _ =>
          val app = ExplorerApp(
            fields = config.fields,
            datasetController = DatasetController.default,
            facetsController = new FacetsController(config.fields, db),
            countController = new CountController(db),
            exportController = new ExportController(db)
          )

          // Add logging, CORS, and (when deployed) GZip "middleware".
          val http = {
            val withLogging =
              Logger[IO](logHeaders = true, logBody = true)(new ExplorerApi(app).routes)

            val corsConfig = CORSConfig(
              anyOrigin = config.localEnv,
              allowCredentials = true,
              maxAge = 1.hour.toSeconds,
              anyMethod = false,
              allowedOrigins = Set("https://app.terra.bio"),
              allowedMethods =
                Some(if (config.localEnv) Set("GET", "POST") else Set("GET"))
            )

            if (config.localEnv) {
              CORS(withLogging, corsConfig)
            } else {
              CORS(GZip(withLogging), corsConfig)
            }
          }

          // NOTE: .serve returns a never-ending stream, so this will only
          // complete on SIGSTOP or SIGINT.
          BlazeServerBuilder[IO]
            .bindHttp(config.port, "0.0.0.0")
            .withHttpApp(http)
            .serve
            .compile
            .lastOrError
        }
    }
}
