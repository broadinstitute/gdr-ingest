package org.broadinstitute.gdr.encode.explorer.fields

import org.broadinstitute.gdr.encode.explorer.db.DbTable
import pureconfig.ConfigReader
import pureconfig.generic.auto._
import pureconfig.generic.semiauto._
import pureconfig.module.enumeratum._

/**
  * Configuration for a field to query / return in faceted searches.
  *
  * @param table DB table to query for this field
  * @param column DB column to query for this field
  * @param displayName name to display in the UI for this field
  * @param fieldType category of this field, determining how it is queried during search
  */
case class FieldConfig(
  table: DbTable,
  column: String,
  displayName: String,
  fieldType: FieldType
) {

  /** Build a representation of this field to send to the UI. */
  def encoded: String = s"${table.entryName}.$column"
}

object FieldConfig {
  implicit val reader: ConfigReader[FieldConfig] = deriveReader
}
