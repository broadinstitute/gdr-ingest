package org.broadinstitute.gdr.encode.explorer.export

import cats.Parallel
import cats.effect.Sync
import cats.implicits._
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.explorer.db.DbClient

import scala.language.higherKinds

/**
  * Component responsible for handling export-to-Terra requests.
  *
  * @tparam M wrapper type capable of suspending synchronous effects
  * @tparam F wrapper type capable of composing instances of `M` in parallel
  * @param dbClient client which knows how to query the DB
  * @param par proof that `F` can compose instances of `M` in parallel
  */
class ExportController[M[_]: Sync, F[_]](dbClient: DbClient[M])(
  implicit par: Parallel[M, F]
) {

  /** Get donor and file JSON for import to Terra. */
  def export(request: ExportRequest): M[Vector[ExportJson]] = {
    for {
      sqlFilters <- dbClient.filtersToSql(request.filter)
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
