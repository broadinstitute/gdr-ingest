package org.broadinstitute.gdr.encode.explorer.facets

import cats.Parallel
import cats.effect.Sync
import cats.implicits._
import org.broadinstitute.gdr.encode.explorer.db.{DbClient, DbTable}
import org.broadinstitute.gdr.encode.explorer.fields.{
  FieldConfig,
  FieldType,
  FieldsConfig
}

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
  fields: FieldsConfig,
  dbClient: DbClient[M]
)(implicit par: Parallel[M, F]) {

  /** Check that the fields defined in config refer to actual DB columns. */
  def validateFields: M[Unit] =
    (
      validateFields(DbTable.Donors, fields.donorFields),
      validateFields(DbTable.Files, fields.fileFields)
    ).parMapN((_, _) => ())

  private def validateFields(table: DbTable, fields: List[FieldConfig]): M[Unit] =
    dbClient.fields(table).flatMap { dbFields =>
      fields.traverse_ { f =>
        val col = f.column
        if (dbFields.contains(col)) {
          ().pure[M]
        } else {
          val err: Throwable = new IllegalStateException(
            s"No such field in table '${table.entryName}': $col"
          )
          err.raiseError[M, Unit]
        }
      }
    }

  /**
    * Get the unique values / counts of all columns configured for faceted search.
    *
    * @param filters filters to apply to the search. Not used for now
    */
  def getFacets(filters: Map[String, Vector[String]]): M[FacetsResponse] = {
    val _ = filters
    val donorCount = dbClient.count(DbTable.Donors)
    val donorFields = getFacets(DbTable.Donors, fields.donorFields)
    val fileFields = getFacets(DbTable.Files, fields.fileFields)
    (donorCount, donorFields, fileFields).parMapN {
      case (count, donors, files) =>
        FacetsResponse(donors ::: files, count)
    }
  }

  private def getFacets(table: DbTable, fields: List[FieldConfig]): M[List[Facet]] =
    fields.parTraverse { f =>
      val counts = f.fieldType match {
        case FieldType.Array   => dbClient.countsByNestedValue(table, f.column)
        case FieldType.Keyword => dbClient.countsByValue(table, f.column)
        case FieldType.Number  => dbClient.countsByRange(table, f.column)
      }

      counts.map { cs =>
        val vals = cs.map { case (v, c) => FacetValue(v, c) }
        Facet(f.displayName, None, s"$table.${f.column}", vals)
      }
    }
}
