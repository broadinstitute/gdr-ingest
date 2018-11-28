package org.broadinstitute.gdr.encode.explorer.db

import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

/** Settings for connecting to a database. */
case class DbConfig(
  driverClassname: String,
  connectURL: String,
  username: String,
  password: String
)

object DbConfig {
  implicit val reader: ConfigReader[DbConfig] = deriveReader
}
