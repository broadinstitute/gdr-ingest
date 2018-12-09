package org.broadinstitute.gdr.encode.explorer.count

import cats.Parallel
import cats.effect.Sync
import cats.implicits._
import org.broadinstitute.gdr.encode.explorer.db.{DbClient, DbTable}
import org.broadinstitute.gdr.encode.explorer.fields.FieldFilter

import scala.language.higherKinds

class CountController[M[_]: Sync, F[_]](dbClient: DbClient[M])(
  implicit par: Parallel[M, F]
) {

  def count(filters: FieldFilter.Filters): M[CountResponse] = {
    val filtersByTable = filters.groupBy(_._1.table)
    val donorsCount =
      count(DbTable.Donors, filtersByTable.getOrElse(DbTable.Donors, Map.empty))
    val filesCount =
      count(DbTable.Files, filtersByTable.getOrElse(DbTable.Files, Map.empty))

    (donorsCount, filesCount).parMapN {
      case (donors, files) => CountResponse(donors, files)
    }
  }

  private def count(table: DbTable, tableFilters: FieldFilter.Filters): M[Long] =
    dbClient.filtersToSql(tableFilters).flatMap(dbClient.countRows(table, _))
}
