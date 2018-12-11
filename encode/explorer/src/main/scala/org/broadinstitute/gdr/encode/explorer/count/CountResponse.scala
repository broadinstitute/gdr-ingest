package org.broadinstitute.gdr.encode.explorer.count

import io.circe.Encoder
import io.circe.derivation.{deriveEncoder, renaming}

case class CountResponse(donorCount: Long, fileCount: Long)

object CountResponse {
  implicit val encoder: Encoder[CountResponse] = deriveEncoder(renaming.snakeCase)
}
