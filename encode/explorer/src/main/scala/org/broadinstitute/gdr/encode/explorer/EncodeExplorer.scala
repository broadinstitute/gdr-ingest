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
      val facetsController = new FacetsController[IO](config.fields, db)
      runServer(config.port, DatasetController.default, facetsController)
    }

  private def runServer(
    port: Int,
    datasetController: DatasetController,
    facetsController: FacetsController[IO]
  ): IO[Unit] = {
    val routes = HttpRoutes
      .of[IO] {
        case GET -> Root / "dataset" =>
          Ok(datasetController.datasetInfo.asJson)
        case GET -> Root / "facets" =>
          Ok(facetsController.getFacets.map(_.asJson))
      }
      .orNotFound

    BlazeServerBuilder[IO]
      .bindHttp(port, "localhost")
      .withHttpApp(Logger(logHeaders = true, logBody = true)(routes))
      .serve
      .compile
      .drain
  }
}
