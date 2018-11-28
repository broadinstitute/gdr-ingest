package org.broadinstitute.gdr.encode.explorer.fields

import pureconfig.ConfigReader
import pureconfig.generic.auto._
import pureconfig.generic.semiauto._
import pureconfig.module.enumeratum._

/**
  * Configuration for a field to query / return in faceted searches.
  *
  * @param column DB column to query for this field
  * @param displayName name to display in the UI for this field
  * @param fieldType category of this field, determining how it is queried during search
  */
case class FieldConfig(column: String, displayName: String, fieldType: FieldType)

object FieldConfig {
  implicit val reader: ConfigReader[FieldConfig] = deriveReader
}
