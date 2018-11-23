package org.broadinstitute.gdr.encode.explorer

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
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
      .as(ExitCode.Success)

  private def run(config: ExplorerConfig): IO[Unit] =
    DbClient.resource[IO](config.db).use { db =>
      val facetsController = new FacetsController(config.fields, db)

      val routes = HttpRoutes.of[IO] {
        case GET -> Root / "api" / "dataset" =>
          Ok(DatasetController.default.datasetInfo.asJson)
        case GET -> Root / "api" / "facets" =>
          Ok(facetsController.getFacets.map(_.asJson))
      }

      val app = Logger[IO](
        logHeaders = config.logging.logHeaders,
        logBody = config.logging.logBodies
      )(routes.orNotFound)

      BlazeServerBuilder[IO]
        .bindHttp(config.port, "0.0.0.0")
        .withHttpApp(app)
        .serve
        .compile
        .drain
    }
}
