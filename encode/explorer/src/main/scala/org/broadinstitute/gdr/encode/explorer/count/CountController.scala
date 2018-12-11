package org.broadinstitute.gdr.encode.explorer.count

import cats.effect.Sync
import cats.implicits._
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.fields.FieldFilter

import scala.language.higherKinds

class CountController[M[_]: Sync, F[_]](dbClient: DbClient[M, F]) {

  def count(filters: FieldFilter.Filters): M[CountResponse] = {
    for {
      sqlFilters <- dbClient.filtersToSql(filters)
      response <- dbClient.countRows(sqlFilters)
    } yield {
      response
    }
  }
}
