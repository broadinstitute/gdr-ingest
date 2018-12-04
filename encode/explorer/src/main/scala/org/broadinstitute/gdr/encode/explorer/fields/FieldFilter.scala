package org.broadinstitute.gdr.encode.explorer.fields

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits._
import cats.kernel.Monoid

/** Collection of constraints to apply to a field in search / export. */
case class FieldFilter(values: NonEmptyList[String])

object FieldFilter {

  /** Convenience alias for mapping from field -> filters to apply to that field. */
  type Filters = Map[FieldConfig, FieldFilter]

  // Methods like 'traverse' need a type with only one variable.
  // The standard work-around is to fix the error type to a constant, which we do here with 'String'.
  private type ParsingValidation[A] = ValidatedNel[String, A]

  private implicit val mon: Monoid[Filters] = new Monoid[Filters] {
    def empty = Map.empty[FieldConfig, FieldFilter]
    def combine(x: Filters, y: Filters): Filters = x ++ y
  }

  /**
    * Parse a collection of 'field=value' pairs into a filters map.
    *
    * @return a filters map if all 'field=value' pairs are well-formed and refer to
    *         actual fields, otherwise a list of error messages
    */
  def parseFilters(
    encodedFilters: List[String],
    fields: List[FieldConfig]
  ): ValidatedNel[String, Filters] = {
    val fieldFilterPairs = encodedFilters
      .filter(_.nonEmpty)
      .traverse[ParsingValidation, (String, String)] { constraint =>
        // Java-ism, returns -1 when '=' isn't in the string:
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

  /**
    * Parse a collection of (field, value) pairs into a filters map.
    *
    * @return a filters map if all (field, value) pairs refer to actual fields,
    *         otherwise a list of error messages
    */
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
