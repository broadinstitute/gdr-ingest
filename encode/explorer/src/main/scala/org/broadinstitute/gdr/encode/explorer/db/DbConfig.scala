package org.broadinstitute.gdr.encode.explorer.db

import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

import scala.concurrent.duration.FiniteDuration

/** Settings for connecting to a database. */
case class DbConfig(
  driverClassname: String,
  connectURL: String,
  username: String,
  password: String,
  leakDetectionTimeout: FiniteDuration
)

object DbConfig {
  implicit val reader: ConfigReader[DbConfig] = deriveReader
}
