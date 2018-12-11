package org.broadinstitute.gdr.encode.explorer.facets

import cats.Parallel
import cats.effect.Sync
import cats.implicits._
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.fields.FieldConfig

import scala.language.higherKinds

/**
  * Component responsible for handling faceted search requests.
  *
  * @tparam M wrapper type capable of suspending synchronous effects
  * @tparam F wrapper type capable of composing instances of `M` in parallel
  * @param fields configuration determining which DB columns should be queried
  *               and returned on faceted search requests
  * @param dbClient client which knows how to query the DB
  * @param par proof that `F` can compose instances of `M` in parallel
  */
class FacetsController[M[_]: Sync, F[_]](
  fields: List[FieldConfig],
  dbClient: DbClient[M, F]
)(implicit par: Parallel[M, F]) {

  /**
    * Get the unique values of all columns configured for faceted search.
    */
  def getFacets: M[FacetsResponse] =
    fields.parTraverse(dbClient.buildFacet).map(FacetsResponse(_))
}
