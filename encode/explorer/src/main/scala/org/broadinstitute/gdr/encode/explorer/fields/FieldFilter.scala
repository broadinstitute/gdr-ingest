package org.broadinstitute.gdr.encode.explorer.fields

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits._
import cats.kernel.Monoid

case class FieldFilter(values: NonEmptyList[String])

object FieldFilter {

  type Filters = Map[FieldConfig, FieldFilter]

  type ParsingValidation[A] = ValidatedNel[String, A]

  private implicit val mon: Monoid[Filters] = new Monoid[Filters] {
    def empty = Map.empty[FieldConfig, FieldFilter]
    def combine(x: Filters, y: Filters): Filters = x ++ y
  }

  def parseFilters(
    encodedFilters: List[String],
    fields: List[FieldConfig]
  ): ValidatedNel[String, Filters] = {
    val fieldFilterPairs = encodedFilters
      .filter(_.nonEmpty)
      .traverse[ParsingValidation, (String, String)] { constraint =>
        val i = constraint.indexOf('=')
        if (i < 0) {
          s"Bad filter: $constraint".invalidNel
        } else {
          (constraint.take(i), constraint.drop(i + 1)).validNel
        }
      }

    fieldFilterPairs.andThen { pairs =>
      NonEmptyList
        .fromList(pairs)
        .fold(mon.empty.validNel[String])(parse(_, fields))
    }
  }

  private def parse(
    fieldFilterPairs: NonEmptyList[(String, String)],
    fields: List[FieldConfig]
  ): ValidatedNel[String, Filters] =
    fieldFilterPairs.groupBy(_._1).toList.foldMap {
      case (field, filters) =>
        fields
          .find(_.encoded == field)
          .fold(s"Invalid field: $field".invalidNel[Filters])(
            cfg => Map(cfg -> FieldFilter(filters.map(_._2))).validNel
          )
    }
}
