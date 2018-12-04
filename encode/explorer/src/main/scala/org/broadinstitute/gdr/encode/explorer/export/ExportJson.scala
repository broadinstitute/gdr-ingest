package org.broadinstitute.gdr.encode.explorer.export

import io.circe.{Encoder, Json, JsonObject}
import io.circe.derivation.deriveEncoder

case class ExportJson(name: Json, entityType: String, attributes: JsonObject)

object ExportJson {
  implicit val encoder: Encoder[ExportJson] = deriveEncoder
}
