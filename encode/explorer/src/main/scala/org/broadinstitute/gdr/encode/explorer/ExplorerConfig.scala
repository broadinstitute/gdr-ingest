package org.broadinstitute.gdr.encode.explorer

import org.broadinstitute.gdr.encode.explorer.db.DbConfig
import org.broadinstitute.gdr.encode.explorer.export.ExportConfig
import org.broadinstitute.gdr.encode.explorer.fields.FieldConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

/**
  * Top-level config for the API server.
  *
  * @param port port the server should bind to
  * @param db config for the server's DB connections
  * @param export config for export-to-Terra
  * @param fields config for the server's field access
  */
case class ExplorerConfig(
  port: Int,
  db: DbConfig,
  export: ExportConfig,
  fields: List[FieldConfig]
)

object ExplorerConfig {
  implicit val reader: ConfigReader[ExplorerConfig] = deriveReader
}
