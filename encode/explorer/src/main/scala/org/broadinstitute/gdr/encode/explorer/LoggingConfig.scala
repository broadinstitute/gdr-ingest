package org.broadinstitute.gdr.encode.explorer

import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

case class LoggingConfig(logHeaders: Boolean, logBodies: Boolean)

object LoggingConfig {
  implicit val reader: ConfigReader[LoggingConfig] = deriveReader
}
