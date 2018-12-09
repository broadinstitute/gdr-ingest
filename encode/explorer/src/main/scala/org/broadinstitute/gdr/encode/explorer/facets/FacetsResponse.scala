package org.broadinstitute.gdr.encode.explorer.facets

import io.circe.Encoder
import io.circe.derivation.{deriveEncoder, renaming}

/**
  * Options for a faceted search.
  */
case class FacetsResponse(facets: List[Facet])

object FacetsResponse {
  implicit val encoder: Encoder[FacetsResponse] = deriveEncoder(renaming.snakeCase)
}
