package org.broadinstitute.gdr.encode.ingest.steps.google

import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.ingest.steps.transform.DeriveActualUris

object Gcs {
  val BlobPathField = "file_gs_path"

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
