package org.broadinstitute.gdr.encode.explorer.dataset

import io.circe.Encoder
import io.circe.derivation.{deriveEncoder, renaming}

/**
  * Dataset information.
  *
  * This will always be name="ENCODE", but for now I'm just mechanically
  * mirroring the Data Explorer UI's expected API.
  */
case class DatasetResponse(name: String)

object DatasetResponse {
  implicit val encoder: Encoder[DatasetResponse] = deriveEncoder(renaming.snakeCase)
}
