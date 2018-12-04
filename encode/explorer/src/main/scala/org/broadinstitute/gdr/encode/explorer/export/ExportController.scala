package org.broadinstitute.gdr.encode.explorer.export

import cats.Parallel
import cats.effect.Sync
import cats.implicits._
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.fields.{FieldConfig, FieldFilter}

import scala.language.higherKinds

class ExportController[M[_]: Sync, F[_]](config: ExportConfig, dbClient: DbClient[M])(
  implicit par: Parallel[M, F]
) {

  def exportUrl(request: ExportRequest): M[ExportResponse] = {
    val filterParam = request.filter.flatMap {
      case (field, filters) => encodeFilter(field, filters)
    }.mkString("|")

    val exportUri = (config.baseUri / "api" / "export")
      .withQueryParam("filter", filterParam)
      .withOptionQueryParam("cohortName", request.cohortName)

    ExportResponse(exportUri).pure[M]
  }

  private def encodeFilter(field: FieldConfig, filters: FieldFilter): List[String] =
    filters.values.map(v => s"${field.encoded}=$v").toList

  def export(request: ExportRequest): M[Vector[ExportJson]] = {
    val sqlFilters = dbClient.filtersToSql(request.filter)

    val donorJson = dbClient.donorStream(sqlFilters)
    val fileJson = dbClient.fileStream(sqlFilters)

    (donorJson, fileJson).parMapN {
      case (donors, files) =>
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
        "items" -> entities.map(_.name).asJson
      )
    )
  )
}
