package org.broadinstitute.gdr.encode.steps.google

object Gcs {
  val BlobPathField = "path"

  def expectedStsUri(stsTargetBucket: String)(httpUri: String): String =
    httpUri.replaceFirst("^https?://", s"gs://$stsTargetBucket/")
}
