package org.broadinstitute.gdr.encode.ingest.steps.load

import better.files.File
import cats.effect.{ConcurrentEffect, ContextShift, Sync, Timer}
import cats.implicits._
import com.google.cloud.storage.StorageOptions
import fs2.Stream
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep
import org.broadinstitute.gdr.encode.ingest.steps.transform.{
  BuildStsManifest,
  ShapeFileMetadata
}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/**
 * Ingest step which replaces input fields for the STS with corresponding
 * post-transfer fields, in prep for loading into the Data Explorer.
 *
 * @param filesMetadata path to files JSON which was used to build an STS manifest
 * @param storageBucket name of the GCS bucket containing transferred ENCODE files
 * @param out path to output file where updated JSON should be written
 * @param ec execution context which should run blocking I/O operations
 */
class SwapFileFields(
  filesMetadata: File,
  storageBucket: String,
  override protected val out: File,
  ec: ExecutionContext
) extends IngestStep {
  import SwapFileFields._

  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] = {
    Stream
      .eval(existingFiles[F])
      .flatMap { filesInCloud =>
        IngestStep
          .readJsonArray(ec)(filesMetadata)
          .map(swapUriFields(storageBucket, filesInCloud))
      }
      .map(swapSizeFields)
      .through(IngestStep.writeJsonArray(ec)(out))
  }

  /**
   * Get all objects currently stored in the transfer bucket.
   *
   * Objects are returned without their leading 'gs://bucket' prefix.
   */
  private def existingFiles[F[_]: Sync]: F[Set[String]] = {
    val S = Sync[F]

    for {
      _ <- S.delay(logger.info(s"Getting files stored in gs://$storageBucket..."))
      options <- S.delay(StorageOptions.getDefaultInstance())
      storage <- S.delay(options.getService())
      maybeBucket <- S.delay(storage.get(storageBucket))
      bucket <- Option(maybeBucket).liftTo[F](
        new IllegalArgumentException(s"Can't access bucket '$storageBucket'")
      )
      response <- S.delay(bucket.list())
      blobs <- S.delay(response.iterateAll())
    } yield {
      blobs.asScala.map(_.getName).toSet[String]
    }
  }

  /**
    * Replace the HTTP pointer in file metadata with a corresponding GCS pointer,
    * if the file has been moved into GCS.
    */
  private def swapUriFields(stsTargetBucket: String, existingFiles: Set[String])(
    fileJson: JsonObject
  ): JsonObject =
    fileJson(ShapeFileMetadata.CloudMetadataField)
      .flatMap(_.as[BuildStsManifest.CloudMetadata].toOption)
      .map(_.url)
      .fold(fileJson.add(FileAvailableField, false.asJson)) { uri =>
        val objectPath = uri.replaceFirst("^https?://", "")
        val fileInCloud = existingFiles.contains(objectPath)

        val updated = if (fileInCloud) {
          val gcsUri = s"gs://$stsTargetBucket/$objectPath"
          val withGcs = fileJson.add(BlobPathField, gcsUri.asJson)

          withGcs(ShapeFileMetadata.FileFormatField)
            .flatMap(_.asString)
            .fold(withGcs) { format =>
              if (format == "bam" && existingFiles.contains(s"$objectPath.bai")) {
                withGcs.add(BlobIndexField, s"$gcsUri.bai".asJson)
              } else {
                withGcs
              }
            }
        } else {
          fileJson
        }

        updated
          .add(FileAvailableField, fileInCloud.asJson)
          .remove(ShapeFileMetadata.CloudMetadataField)
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

object SwapFileFields {
  val BlobPathField = "file_gs_path"
  val BlobIndexField = "file_index_gs_path"
  val FileAvailableField = "file_available_in_gcs"
  val FileSizeScaledField = "file_size_mb"

  // Same calculation used by Cromwell to calculate meaning of MiB:
  val BytesPerMb: Long = 1L << 20
}
