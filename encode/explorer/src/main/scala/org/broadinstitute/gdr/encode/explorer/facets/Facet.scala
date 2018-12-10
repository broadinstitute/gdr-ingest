package org.broadinstitute.gdr.encode.explorer.facets

import io.circe.Encoder
import io.circe.derivation.{deriveEncoder, renaming}
import io.circe.syntax._

/** A facet to expose in search. */
sealed trait Facet {

  /** Facet display name, i.e. "Gender". */
  def displayName: String

  /** Combined DB table/column mapping to this facet. */
  def dbName: String
}

object Facet {
  case class KeywordFacet(displayName: String, dbName: String, values: List[String])
      extends Facet
  case class RangeFacet(displayName: String, dbName: String, min: Double, max: Double)
      extends Facet

  implicit val kwEncoder: Encoder[KeywordFacet] = deriveEncoder(renaming.snakeCase)
  implicit val rangeEncoder: Encoder[RangeFacet] = deriveEncoder(renaming.snakeCase)

  implicit val encoder: Encoder[Facet] = {
    case kw: KeywordFacet => kw.asJson.mapObject(_.add("facet_type", "list".asJson))
    case r: RangeFacet    => r.asJson.mapObject(_.add("facet_type", "range".asJson))
  }
}
