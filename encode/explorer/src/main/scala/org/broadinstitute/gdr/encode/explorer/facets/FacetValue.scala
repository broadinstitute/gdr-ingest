package org.broadinstitute.gdr.encode.explorer.facets

import io.circe.derivation.annotations.{Configuration, JsonCodec}

/**
  * A facet value, for example "Male"/34.
  *
  * @param name one value for a facet, i.e. "Male" for the "Gender" facet
  * @param count number of entities in the current selection with this value
  *              for the enclosing facet
  */
@JsonCodec(Configuration.encodeOnly.withSnakeCaseMemberNames)
case class FacetValue(name: String, count: Long)
