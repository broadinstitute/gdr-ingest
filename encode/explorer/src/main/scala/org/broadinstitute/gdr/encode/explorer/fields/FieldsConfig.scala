package org.broadinstitute.gdr.encode.explorer.fields

import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

/**
  * Configuration for all fields to query / return during faceted search.
  *
  * @param donorFields fields to query from the donors table
  * @param fileFields fields to query from the files table
  */
case class FieldsConfig(donorFields: List[FieldConfig], fileFields: List[FieldConfig])

object FieldsConfig {
  implicit val reader: ConfigReader[FieldsConfig] = deriveReader
}
