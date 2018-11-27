package org.broadinstitute.gdr.encode.ingest.steps

import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.ingest.steps.transform.{
  DeriveActualUris,
  ShapeFileMetadata
}

/**
  * Common functionality for ingest steps which transform generic cleaned JSON
  * into backend-specific payloads.
  */
object ExportTransforms {
  val BlobPathField = "file_gs_path"
  val FileSizeScaledField = "file_size_mb"

  // Same calculation used by Cromwell to calculate meaning of MiB:
  val BytesPerMb: Long = 1L << 20

  /**
    * Transform file JSON to reflect the manual data-transfer steps that *should*
    * happen between generating cleaned JSON and loading that JSON into a back-end.
    */
  def swapFileFields(storageBucket: String)(fileJson: JsonObject): JsonObject =
    swapSizeFields(swapUriFields(storageBucket)(fileJson))

  /**
    * Derive a target GCS path for a source HTTP link, assuming the HTTP object was
    * moved into GCS using Google's Storage Transfer Service.
    */
  private def expectedStsUri(stsTargetBucket: String, httpUri: String): String =
    httpUri.replaceFirst("^https?://", s"gs://$stsTargetBucket/")

  /**
    * Replace the HTTP pointer in file metadata with a corresponding GCS pointer,
    * assuming the file has been moved using the STS.
    */
  private def swapUriFields(stsTargetBucket: String)(fileJson: JsonObject): JsonObject =
    fileJson(DeriveActualUris.DownloadUriField)
      .flatMap(_.asString)
      .fold(fileJson) { uri =>
        fileJson
          .add(BlobPathField, expectedStsUri(stsTargetBucket, uri).asJson)
          .remove(DeriveActualUris.DownloadUriField)
      }

  /**
    * Replace the size-in-bytes of a file with the size-in-MiB.
    *
    * Cromwell maps WDL's `Int` type to a Scala `Int` instead of a `Long`,
    * so users can hit overflows when trying to load the size as an input for large files.
    *
    * :facepalm:
    */
  private def swapSizeFields(fileJson: JsonObject): JsonObject = {
    val maybeSizeMb = fileJson(ShapeFileMetadata.FileSizeField)
      .flatMap(_.as[Long].toOption)
      .map(_ / BytesPerMb)
      .map(_.asJson)

    maybeSizeMb
      .fold(fileJson)(fileJson.add(FileSizeScaledField, _))
      .remove(ShapeFileMetadata.FileSizeField)
  }
}
