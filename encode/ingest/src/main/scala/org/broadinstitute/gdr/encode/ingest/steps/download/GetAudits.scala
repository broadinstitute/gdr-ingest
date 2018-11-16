package org.broadinstitute.gdr.encode.ingest.steps.download

import better.files.File
import io.circe.JsonObject
import org.broadinstitute.gdr.encode.ingest.EncodeFields

import scala.concurrent.ExecutionContext

class GetAudits(fileMetadata: File, out: File, ec: ExecutionContext)
    extends GetFromPreviousMetadataStep(fileMetadata, out, ec) {

  final override val entityType = "File"
  final override val refField = EncodeFields.EncodeIdField
  final override val manyRefs = false

  // Audit is a magic, undocumented field in the ENCODE metadata.
  // Requesting frame=audit forces it to be generated.
  final override val searchFrame = EncodeFields.EncodeAuditField

  final override def transformMetadata(fileJson: JsonObject): JsonObject =
    GetAudits.FieldsToRetain.foldLeft(JsonObject.empty) { (acc, field) =>
      fileJson(field).fold(acc)(acc.add(field, _))
    }
}

object GetAudits {
  val FieldsToRetain = Set(EncodeFields.EncodeIdField, EncodeFields.EncodeAuditField)
}
