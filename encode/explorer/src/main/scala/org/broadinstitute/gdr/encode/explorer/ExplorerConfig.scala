package org.broadinstitute.gdr.encode.explorer

import org.broadinstitute.gdr.encode.explorer.db.DbConfig
import org.broadinstitute.gdr.encode.explorer.fields.FieldsConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

/**
  * Top-level config for the API server.
  *
  * @param port port the server should bind to
  * @param logging config for the server's HTTP logging
  * @param db config for the server's DB connections
  * @param fields config for the server's field access
  */
case class ExplorerConfig(
  port: Int,
  logging: LoggingConfig,
  db: DbConfig,
  fields: FieldsConfig
)

object ExplorerConfig {
  implicit val reader: ConfigReader[ExplorerConfig] = deriveReader
}
