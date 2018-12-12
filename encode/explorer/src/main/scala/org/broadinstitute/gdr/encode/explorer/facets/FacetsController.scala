package org.broadinstitute.gdr.encode.explorer.facets

import cats.effect.{ContextShift, IO}
import cats.implicits._
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.fields.FieldConfig

/**
  * Component responsible for handling faceted search requests.
  *
  * @param fields configuration determining which DB columns should be queried
  *               and returned on faceted search requests
  * @param dbClient client which knows how to query the DB
  * @param cs proof the controller can shift `IO` computations onto separate threads
  */
class FacetsController(fields: List[FieldConfig], dbClient: DbClient)(
  implicit cs: ContextShift[IO]
) {

  /** Get the unique values of all columns configured for faceted search. */
  def getFacets: IO[FacetsResponse] =
    fields.parTraverse(dbClient.buildFacet).map(FacetsResponse(_))
}
