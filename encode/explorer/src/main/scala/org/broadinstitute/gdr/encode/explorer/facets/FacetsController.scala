package org.broadinstitute.gdr.encode.explorer.facets

import cats.Parallel
import cats.effect.Sync
import cats.implicits._
import doobie.util.fragment.Fragment
import org.broadinstitute.gdr.encode.explorer.db.{DbClient, DbTable}
import org.broadinstitute.gdr.encode.explorer.fields.{FieldConfig, FieldFilter}

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
  dbClient: DbClient[M]
)(implicit par: Parallel[M, F]) {

  /**
    * Get the unique values / counts of all columns configured for faceted search.
    *
    * @param filters filters to apply to the search.
    */
  def getFacets(filters: FieldFilter.Filters): M[FacetsResponse] = {
    val sqlFilters = dbClient.filtersToSql(filters)

    val donorCount = dbClient.countRows(DbTable.Donors, sqlFilters)
    val facetValues = getFacets(fields, sqlFilters)

    (donorCount, facetValues).parMapN {
      case (count, facets) =>
        FacetsResponse(facets, count)
    }
  }

  /** Get facet values for fields, under a set of constraints. */
  private def getFacets(
    fields: List[FieldConfig],
    filters: Map[FieldConfig, Fragment]
  ): M[List[Facet]] =
    fields.parTraverse { field =>
      /*
       * Remove any filters for the current field from the counting query because
       * if we didn't, as soon as a user selected a facet value in the UI every other
       * option for that facet would disappear.
       */
      dbClient
        .countValues(field, filters)
        .map { cs =>
          val vals = cs.map { case (value, count) => FacetValue(value, count) }
          Facet(field.displayName, None, field.encoded, vals)
        }
    }
}
