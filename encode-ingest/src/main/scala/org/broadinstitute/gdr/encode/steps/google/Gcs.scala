package org.broadinstitute.gdr.encode.steps.google

import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.transform.DeriveActualUris

object Gcs {
  val BlobPathField = "path"

  private def expectedStsUri(stsTargetBucket: String, httpUri: String): String =
    httpUri.replaceFirst("^https?://", s"gs://$stsTargetBucket/")

  def swapUriFields(stsTargetBucket: String)(fileJson: JsonObject): JsonObject =
    fileJson(DeriveActualUris.DownloadUriField)
      .flatMap(_.asString)
      .fold(fileJson) { uri =>
        fileJson
          .add(BlobPathField, expectedStsUri(stsTargetBucket, uri).asJson)
          .remove(DeriveActualUris.DownloadUriField)
      }
}
