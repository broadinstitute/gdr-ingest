package org.broadinstitute.gdr.encode.explorer.export

import java.net.URLEncoder

import io.circe.Encoder
import io.circe.derivation.{deriveEncoder, renaming}
import org.http4s.Uri

/**
  * Response to an "export-URL" request, pointing to a JSON payload for Terra to import.
  *
  * @param url URI which, when pulled, will produce JSON for Terra to import into a workspace
  */
case class ExportResponse(url: Uri)

object ExportResponse {
  // Force-encode the entirety of the URI before sending it to the UI.
  implicit val uri: Encoder[Uri] = Encoder[String].contramap { uri =>
    URLEncoder.encode(uri.renderString, "UTF-8")
  }
  implicit val encoder: Encoder[ExportResponse] = deriveEncoder(renaming.snakeCase)
}
