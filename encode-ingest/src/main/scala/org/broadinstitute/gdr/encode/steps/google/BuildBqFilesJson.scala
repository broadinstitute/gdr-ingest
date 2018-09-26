package org.broadinstitute.gdr.encode.steps.google

import better.files.File
import cats.effect.Effect
import fs2.Stream
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep
import org.broadinstitute.gdr.encode.steps.transform.DeriveActualUris

import scala.language.higherKinds

class BuildBqFilesJson(
  fileMetadata: File,
  rawStorageBucket: String,
  override protected val out: File
) extends IngestStep {

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
    IngestStep
      .readJsonArray(fileMetadata)
      .map(swapUriFields)
      .map(_.asJson.noSpaces)
      .to(IngestStep.writeLines(out))

  private def swapUriFields(fileJson: JsonObject): JsonObject =
    fileJson(DeriveActualUris.DownloadUriField)
      .flatMap(_.asString)
      .fold(fileJson) { uri =>
        fileJson
          .add(Gcs.BlobPathField, Gcs.expectedStsUri(rawStorageBucket)(uri).asJson)
          .remove(DeriveActualUris.DownloadUriField)
      }
}
