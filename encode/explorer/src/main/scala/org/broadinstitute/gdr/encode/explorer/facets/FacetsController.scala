package org.broadinstitute.gdr.encode.explorer.facets

import cats.effect.Sync
import cats.implicits._
import org.broadinstitute.gdr.encode.explorer.db.DbClient
import org.broadinstitute.gdr.encode.explorer.fields.{FieldConfig, FieldsConfig}

import scala.language.higherKinds

class FacetsController[F[_]: Sync](
  fields: FieldsConfig,
  dbClient: DbClient[F]
) {

  def getFacets: F[FacetsResponse] = {
    val donorFields = getFacets("donors", fields.donorFields)
    val fileFields = getFacets("files", fields.fileFields)
    (donorFields, fileFields).mapN {
      case (donors, files) =>
        FacetsResponse(donors ::: files, donors.flatMap(_.values).foldMap(_.count))
    }
  }

  private def getFacets(table: String, fields: List[FieldConfig]): F[List[Facet]] =
    fields.traverse[F, Facet] { f =>
      dbClient
        .countsByValue(table, f.column)
        .map { case (value, count) => FacetValue(value, count) }
        .compile
        .toList
        .map(Facet(f.displayName, None, s"$table.${f.column}", _))
    }
}
