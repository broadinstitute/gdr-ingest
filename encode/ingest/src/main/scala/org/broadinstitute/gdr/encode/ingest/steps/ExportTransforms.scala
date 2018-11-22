package org.broadinstitute.gdr.encode.ingest.steps

import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.ingest.steps.transform.{
  DeriveActualUris,
  ShapeFileMetadata
}

object ExportTransforms {
  val BlobPathField = "file_gs_path"
  val FileSizeScaledField = "file_size_mb"

  // Same calculation used by Cromwell to calculate meaning of MiB:
  val BytesPerMb: Long = 1L << 20

  def swapFileFields(storageBucket: String)(fileJson: JsonObject): JsonObject = {
    val uriSwapped = ExportTransforms.swapUriFields(storageBucket)(fileJson)
    val maybeSizeMb = uriSwapped(ShapeFileMetadata.FileSizeField)
      .flatMap(_.as[Long].toOption)
      .map(_ / BytesPerMb)
      .map(_.asJson)

    maybeSizeMb
      .fold(uriSwapped)(uriSwapped.add(FileSizeScaledField, _))
      .remove(ShapeFileMetadata.FileSizeField)
  }

  private def expectedStsUri(stsTargetBucket: String, httpUri: String): String =
    httpUri.replaceFirst("^https?://", s"gs://$stsTargetBucket/")

  private def swapUriFields(stsTargetBucket: String)(fileJson: JsonObject): JsonObject =
    fileJson(DeriveActualUris.DownloadUriField)
      .flatMap(_.asString)
      .fold(fileJson) { uri =>
        fileJson
          .add(BlobPathField, expectedStsUri(stsTargetBucket, uri).asJson)
          .remove(DeriveActualUris.DownloadUriField)
      }
}
