package org.broadinstitute.gdr.encode.explorer.facets

import io.circe.Encoder
import io.circe.derivation.{deriveEncoder, renaming}

/**
  * A facet.
  *
  * @param name facet display name, i.e. "Gender"
  * @param dbName the name of the DB column mapping to this facet
  */
case class Facet(
  name: String,
  dbName: String,
  values: FacetValues
)

object Facet {
  implicit val encoder: Encoder[Facet] = deriveEncoder(renaming.snakeCase)
}
