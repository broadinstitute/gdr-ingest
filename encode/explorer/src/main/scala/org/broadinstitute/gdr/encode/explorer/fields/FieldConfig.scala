package org.broadinstitute.gdr.encode.explorer.fields

import pureconfig.ConfigReader
import pureconfig.generic.auto._
import pureconfig.generic.semiauto._
import pureconfig.module.enumeratum._

case class FieldConfig(column: String, displayName: String, fieldType: FieldType)

object FieldConfig {
  implicit val reader: ConfigReader[FieldConfig] = deriveReader
}
