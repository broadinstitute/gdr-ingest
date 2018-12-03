package org.broadinstitute.gdr.encode.explorer.export

import java.net.URLEncoder

import io.circe.Encoder
import io.circe.derivation.{deriveEncoder, renaming}
import org.http4s.Uri

case class ExportResponse(url: Uri)

object ExportResponse {
  implicit val uri: Encoder[Uri] = Encoder[String].contramap { uri =>
    URLEncoder.encode(uri.renderString, "UTF-8")
  }
  implicit val encoder: Encoder[ExportResponse] = deriveEncoder(renaming.snakeCase)
}
