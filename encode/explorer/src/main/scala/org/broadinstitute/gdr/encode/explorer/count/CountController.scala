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
    for {
      sqlFilters <- dbClient.filtersToSql(filters)
      filtersByTable = sqlFilters.groupBy(_._1.table)
      donorCount = dbClient.countRows(
        DbTable.Donors,
        filtersByTable.getOrElse(DbTable.Donors, Map.empty)
      )
      fileCount = dbClient.countRows(
        DbTable.Files,
        filtersByTable.getOrElse(DbTable.Files, Map.empty)
      )
      (donors, files) <- (donorCount, fileCount).parTupled
    } yield {
      CountResponse(donors, files)
    }
  }
}
