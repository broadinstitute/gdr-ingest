package org.broadinstitute.gdr.encode.explorer.fields

import enumeratum.{Enum, EnumEntry}

/** Category determining how a field should be queried in faceted search. */
sealed trait FieldType extends EnumEntry {

  /** Predicate checking if this field type corresponds to a given DB type. */
  def matches(dbType: String): Boolean
}

object FieldType extends Enum[FieldType] {
  override val values = findValues

  /** Category for fields which should have unique values counted. */
  case object Keyword extends FieldType {
    override def matches(dbType: String): Boolean =
      Set("boolean", "text", "character varying").contains(dbType)
  }

  /** Category for fields which should be counted within histogram bins. */
  case object Number extends FieldType {
    override def matches(dbType: String): Boolean =
      Set("integer", "real", "smallint", "bigint", "double precision").contains(dbType)
  }

  /** Category for fields which should be un-nested before counting unique values. */
  case object Array extends FieldType {
    override def matches(dbType: String): Boolean = dbType == "ARRAY"
  }
}
