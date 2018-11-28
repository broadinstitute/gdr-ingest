package org.broadinstitute.gdr.encode.explorer.facets

import cats.Parallel
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import doobie.util.fragment.Fragment
import org.broadinstitute.gdr.encode.explorer.db.{DbClient, DbTable}
import org.broadinstitute.gdr.encode.explorer.fields.{FieldConfig, FieldsConfig}

import scala.language.higherKinds

/**
  * Component responsible for handling faceted search requests.
  *
  * @tparam M wrapper type capable of suspending synchronous effects
  * @tparam F wrapper type capable of composing instances of `M` in parallel
  * @param fieldsConfig configuration determining which DB columns should be queried
  *                     and returned on faceted search requests
  * @param dbClient client which knows how to query the DB
  * @param par proof that `F` can compose instances of `M` in parallel
  */
class FacetsController[M[_]: Sync, F[_]](
  fieldsConfig: FieldsConfig,
  dbClient: DbClient[M]
)(implicit par: Parallel[M, F]) {

  private val fields: Map[DbTable, List[FieldConfig]] = Map(
    DbTable.Donors -> fieldsConfig.donorFields,
    DbTable.Files -> fieldsConfig.fileFields
  )

  /**
    * Check that the fields defined in config refer to actual DB columns,
    * with expected types.
    */
  def validateFields: M[Unit] =
    DbTable.values.toList.parTraverse_(t => dbClient.validateFields(t, fields(t)))

  /**
    * Get the unique values / counts of all columns configured for faceted search.
    *
    * @param filters filters to apply to the search.
    */
  def getFacets(filters: FacetsController.Filters): M[FacetsResponse] = {

    val donorFilters = getFilters(DbTable.Donors, filters)
    val fileFilters = getFilters(DbTable.Files, filters)
    val fileFiltersWithDonors = if (donorFilters.isEmpty) {
      fileFilters
    } else {
      fileFilters +
        ("donor_ids" -> dbClient.whereDonorIncluded(donorFilters.values.toList))
    }

    val donorCount = dbClient.countRows(DbTable.Donors, donorFilters.values.toList)
    val donorFields = getFacets(DbTable.Donors, fields(DbTable.Donors), donorFilters)
    val fileFields =
      getFacets(DbTable.Files, fields(DbTable.Files), fileFiltersWithDonors)
    (donorCount, donorFields, fileFields).parMapN {
      case (count, donors, files) =>
        FacetsResponse(donors ::: files, count)
    }
  }

  private def getFilters(
    table: DbTable,
    filters: FacetsController.Filters
  ): Map[String, Fragment] =
    fields(table).flatMap { f =>
      filters
        .get(s"${table.entryName}.${f.column}")
        .map(fs => f.column -> dbClient.whereFiltersMatch(f, fs))
    }.toMap

  private def getFacets(
    table: DbTable,
    fields: List[FieldConfig],
    filters: Map[String, Fragment]
  ): M[List[Facet]] =
    fields.parTraverse { f =>
      dbClient.countValues(table, f, (filters - f.column).values.toList).map { cs =>
        val vals = cs.map { case (v, c) => FacetValue(v, c) }
        Facet(f.displayName, None, s"${table.entryName}.${f.column}", vals)
      }
    }
}

object FacetsController {
  type Filters = Map[String, NonEmptyList[String]]
}
