package org.broadinstitute.gdr.encode.explorer.export

import cats.effect.Sync
import cats.implicits._
import io.circe.Json
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.fields.{FieldConfig, FieldFilter}

import scala.language.higherKinds

class ExportController[F[_]: Sync](config: ExportConfig, dbClient: DbClient[F]) {

  def exportUrl(request: ExportRequest): F[ExportResponse] = {
    val filterParam = request.filter.flatMap {
      case (field, filters) => encodeFilter(field, filters)
    }.mkString("|")

    val exportUri = (config.baseUri / "api" / "export")
      .withQueryParam("filter", filterParam)
      .withOptionQueryParam("cohortName", request.cohortName)

    ExportResponse(exportUri).pure[F]
  }

  private def encodeFilter(field: FieldConfig, filters: FieldFilter): List[String] =
    filters.values.map(v => s"${field.encoded}=$v").toList

  // FIXME: Ideally this would return a Stream, but Terra's import UI can't handle chunked payloads.
  // Patch support into Terra?
  def export(request: ExportRequest): List[Json] = {
    val _ = (request, dbClient)
    Nil
  }
}
