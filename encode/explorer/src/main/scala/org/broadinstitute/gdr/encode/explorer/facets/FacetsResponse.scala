package org.broadinstitute.gdr.encode.explorer.facets

import io.circe.Encoder
import io.circe.derivation.{deriveEncoder, renaming}

/**
  * Results from a faceted search.
  *
  * @param count number of entities represented by current facet selection
  */
case class FacetsResponse(facets: List[Facet], count: Long)

object FacetsResponse {
  implicit val encoder: Encoder[FacetsResponse] = deriveEncoder(renaming.snakeCase)
}
