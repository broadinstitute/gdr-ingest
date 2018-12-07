package org.broadinstitute.gdr.encode.explorer.facets

import io.circe.Encoder
import io.circe.derivation.{deriveEncoder, renaming}
import io.circe.syntax._

sealed trait FacetValues

object FacetValues {

  case class ValueList(values: List[ListValue]) extends FacetValues
  case class ValueRange(low: Double, high: Double, count: Long) extends FacetValues

  /**
    * A facet value, for example "Male"/34.
    *
    * @param name one value for a facet, i.e. "Male" for the "Gender" facet
    * @param count number of entities in the current selection with this value
    *              for the enclosing facet
    */
  case class ListValue(name: String, count: Long)

  implicit val valueEncoder: Encoder[ListValue] = deriveEncoder(renaming.snakeCase)
  implicit val listEncoder: Encoder[ValueList] = deriveEncoder(renaming.snakeCase)
  implicit val rangeEncoder: Encoder[ValueRange] = deriveEncoder(renaming.snakeCase)

  implicit val encoder: Encoder[FacetValues] = {
    case l: ValueList  => l.asJson.mapObject(_.add("value_type", "list".asJson))
    case r: ValueRange => r.asJson.mapObject(_.add("value_type", "range".asJson))
  }
}
