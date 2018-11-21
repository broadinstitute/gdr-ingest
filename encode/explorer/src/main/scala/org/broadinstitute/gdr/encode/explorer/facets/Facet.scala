package org.broadinstitute.gdr.encode.explorer.facets

import io.circe.derivation.annotations.{Configuration, JsonCodec}

/**
  * A facet.
  *
  * @param name facet display name, i.e. "Gender"
  * @param description optional facet description
  * @param esFieldName the name of the DB column mapping to this facet.
  *                    The "es" prefix is there to mirror what the Data
  *                    Explorer UI expects
  */
@JsonCodec(Configuration.encodeOnly.withSnakeCaseMemberNames)
case class Facet(
  name: String,
  description: Option[String],
  esFieldName: String,
  values: List[FacetValue]
)
