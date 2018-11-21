package org.broadinstitute.gdr.encode.explorer

import org.broadinstitute.gdr.encode.explorer.db.DbConfig
import org.broadinstitute.gdr.encode.explorer.fields.FieldsConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

case class ExplorerConfig(port: Int, db: DbConfig, fields: FieldsConfig)

object ExplorerConfig {
  implicit val reader: ConfigReader[ExplorerConfig] = deriveReader
}
