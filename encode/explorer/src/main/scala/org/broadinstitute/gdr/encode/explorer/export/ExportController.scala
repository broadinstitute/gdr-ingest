package org.broadinstitute.gdr.encode.explorer.export

import cats.effect.{ContextShift, IO}
import cats.implicits._
import doobie.util.fragment.Fragment
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.explorer.count.CountResponse
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.fields.FieldConfig

/**
  * Component responsible for handling export-to-Terra requests.
  *
  * @param dbClient client which knows how to query the DB
  * @param cs proof the controller can shift `IO` computations onto separate threads
  */
class ExportController(dbClient: DbClient)(implicit cs: ContextShift[IO]) {

  /** Get donor and file JSON for import to Terra. */
  def export(request: ExportRequest): IO[Vector[ExportJson]] =
    for {
      sqlFilters <- dbClient.filtersToSql(request.filter)
      _ <- checkSize(sqlFilters)
      (donors, files) <- (
        dbClient.getDonorJson(sqlFilters),
        dbClient.getFileJson(sqlFilters)
      ).parTupled
    } yield {
      Vector.concat(
        donors,
        files,
        request.cohortName.fold(Vector.empty[ExportJson]) { cohort =>
          Vector(
            buildSet(cohort, "donor", donors),
            buildSet(cohort, "file", files)
          )
        }
      )
    }

  /** Ensure that an export using the given filters will be a "safe" size. */
  private def checkSize(filters: Map[FieldConfig, Fragment]): IO[Unit] = {
    import ExportController._

    dbClient.countRows(filters).flatMap {
      case CountResponse(donors, files) =>
        val total = donors + files
        if (total == 0) {
          new IllegalExportSize("Nothing to export for given filters")
            .raiseError[IO, Unit]
        } else if (total > MaxExport) {
          new IllegalExportSize(
            s"Export too large: Got $total entities, max is $MaxExport"
          ).raiseError[IO, Unit]
        } else {
          ().pure[IO]
        }
    }
  }

  /** Build an entity set over JSON-to-export. */
  private def buildSet(
    setName: String,
    entityType: String,
    entities: Vector[ExportJson]
  ): ExportJson = ExportJson(
    name = setName.asJson,
    entityType = s"${entityType}_set",
    attributes = JsonObject(
      s"${entityType}s" -> Json.obj(
        "itemsType" -> "EntityReference".asJson,
        "items" -> entities
          .map(e => Json.obj("entityType" -> entityType.asJson, "entityName" -> e.name))
          .asJson
      )
    )
  )
}

object ExportController {
  val MaxExport = 10000
  class IllegalExportSize(message: String) extends Throwable(message)
}
