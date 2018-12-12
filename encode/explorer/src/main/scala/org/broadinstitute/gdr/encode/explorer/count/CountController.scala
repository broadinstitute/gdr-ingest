package org.broadinstitute.gdr.encode.explorer.count

import cats.effect.IO
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.fields.FieldFilter

/**
  * Component responsible for handling count requests.
  *
  * @param dbClient client which knows how to query the DB
  */
class CountController(dbClient: DbClient) {

  /** Count the number of entities matching the given filters. */
  def count(filters: FieldFilter.Filters): IO[CountResponse] =
    for {
      sqlFilters <- dbClient.filtersToSql(filters)
      response <- dbClient.countRows(sqlFilters)
    } yield {
      response
    }
}
