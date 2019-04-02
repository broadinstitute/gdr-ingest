package org.broadinstitute.gdr.encode.ingest.steps.extract

import better.files.File
import io.circe.JsonObject
import io.circe.syntax._

import scala.concurrent.ExecutionContext

class GetFiles(in: File, out: File, ec: ExecutionContext)
    extends GetFromPreviousMetadataStep(in, out, ec) {

  final override val entityType = "File"
  final override val refField = "files"
  final override val manyRefs = true

  final override def keepMetadata(metadata: JsonObject): Boolean =
    metadata("no_file_available").fold(true)(_.equals(false.asJson)) &&
      metadata("restricted").fold(true)(_.equals(false.asJson))
}
