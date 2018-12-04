package org.broadinstitute.gdr.encode.explorer.export

import org.http4s.Uri
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._
import pureconfig.module.http4s._

/**
  * Configuration for deployment-specific details of export.
  *
  * @param baseUri the URI of the deployed explorer which will serve
  *                `export` JSON requests
  */
case class ExportConfig(baseUri: Uri)

object ExportConfig {
  implicit val reader: ConfigReader[ExportConfig] = deriveReader
}
