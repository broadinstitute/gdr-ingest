package org.broadinstitute.gdr.encode.explorer.facets

import io.circe.derivation.annotations.{Configuration, JsonCodec}

/**
  * Results from a faceted search.
  *
  * @param count number of entities represented by current facet selection
  */
@JsonCodec(Configuration.encodeOnly.withSnakeCaseMemberNames)
case class FacetsResponse(facets: List[Facet], count: Long)
