package org.broadinstitute.gdr.encode.explorer.facets

import io.circe.Encoder
import io.circe.derivation.{deriveEncoder, renaming}

/**
  * A facet.
  *
  * @param name facet display name, i.e. "Gender"
  * @param description optional facet description
  * @param esFieldName the name of the DB column mapping to this facet.
  *                    The "es" prefix is there to mirror what the Data
  *                    Explorer UI expects
  */
case class Facet(
  name: String,
  description: Option[String],
  esFieldName: String,
  values: List[FacetValue]
)

object Facet {
  implicit val encoder: Encoder[Facet] = deriveEncoder(renaming.snakeCase)
}
