package org.broadinstitute.gdr.encode.explorer.fields

import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

case class FieldsConfig(donorFields: List[FieldConfig], fileFields: List[FieldConfig])

object FieldsConfig {
  implicit val reader: ConfigReader[FieldsConfig] = deriveReader
}
