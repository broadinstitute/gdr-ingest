package org.broadinstitute.gdr.encode.explorer.facets

import cats.Parallel
import cats.effect.Sync
import cats.implicits._
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.fields.{
  FieldConfig,
  FieldType,
  FieldsConfig
}

import scala.language.higherKinds

class FacetsController[M[_]: Sync, F[_]](
  fields: FieldsConfig,
  dbClient: DbClient[M]
)(implicit par: Parallel[M, F]) {

  def validateFields: M[Unit] =
    (
      validateFields("donors", fields.donorFields),
      validateFields("files", fields.fileFields)
    ).parMapN((_, _) => ())

  private def validateFields(table: String, fields: List[FieldConfig]): M[Unit] =
    dbClient.fields(table).flatMap { dbFields =>
      val badFields = fields.map(_.column).toSet[String].diff(dbFields)
      if (badFields.isEmpty) {
        ().pure[M]
      } else {
        val err: Throwable = new IllegalStateException(
          s"No such fields in table '$table': ${badFields.mkString(",")}"
        )
        err.raiseError[M, Unit]
      }
    }

  def getFacets: M[FacetsResponse] = {
    val donorCount = dbClient.count("donors")
    val donorFields = getFacets("donors", fields.donorFields)
    val fileFields = getFacets("files", fields.fileFields)
    (donorCount, donorFields, fileFields).parMapN {
      case (count, donors, files) =>
        FacetsResponse(donors ::: files, count)
    }
  }

  private def getFacets(table: String, fields: List[FieldConfig]): M[List[Facet]] =
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
