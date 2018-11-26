package org.broadinstitute.gdr.encode.explorer

import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

/**
  * Config for how the API server logs requests and responses.
  *
  * @param logHeaders if true, the server will log the headers of all
  *                   requests and responses
  * @param logBodies if true, the server will log the bodies of all
  *                  requests and responses
  */
case class LoggingConfig(logHeaders: Boolean, logBodies: Boolean)

object LoggingConfig {
  implicit val reader: ConfigReader[LoggingConfig] = deriveReader
}
