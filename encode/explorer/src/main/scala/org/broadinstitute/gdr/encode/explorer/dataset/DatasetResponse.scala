package org.broadinstitute.gdr.encode.explorer.dataset

import io.circe.derivation.annotations.{Configuration, JsonCodec}

/**
  * Dataset information.
  *
  * This will always be name="ENCODE", but for now I'm just mechanically
  * mirroring the Data Explorer UI's expected API.
  */
@JsonCodec(Configuration.encodeOnly.withSnakeCaseMemberNames)
case class DatasetResponse(name: String)
