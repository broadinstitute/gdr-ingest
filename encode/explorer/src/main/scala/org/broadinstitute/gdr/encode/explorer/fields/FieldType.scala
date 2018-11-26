package org.broadinstitute.gdr.encode.explorer.fields

import enumeratum.{Enum, EnumEntry}

/** Category determining how a field should be queried in faceted search. */
sealed trait FieldType extends EnumEntry

object FieldType extends Enum[FieldType] {
  override val values = findValues

  /** Category for fields which should have unique values counted. */
  case object Keyword extends FieldType
  /** Category for fields which should be counted within histogram bins. */
  case object Number extends FieldType
  /** Category for fields which should be un-nested before counting unique values. */
  case object Array extends FieldType
}
