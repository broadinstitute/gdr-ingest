package org.broadinstitute.gdr.encode.explorer.fields

import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

case class FieldConfig(column: String, displayName: String)

object FieldConfig {
  implicit val reader: ConfigReader[FieldConfig] = deriveReader
}
