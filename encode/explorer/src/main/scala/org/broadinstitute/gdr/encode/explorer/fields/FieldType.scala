package org.broadinstitute.gdr.encode.explorer.fields

import enumeratum.{Enum, EnumEntry}

sealed trait FieldType extends EnumEntry

object FieldType extends Enum[FieldType] {
  override val values = findValues

  case object Keyword extends FieldType
  case object Number extends FieldType
  case object Array extends FieldType
}
