package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import fs2.{Pure, Scheduler, Stream}
import io.circe.JsonObject

import scala.concurrent.ExecutionContext

class GetAudits(in: File, out: File)(implicit ec: ExecutionContext, s: Scheduler)
    extends GetFromPreviousMetadataStep(in, out) {

  final override val entityType = "Experiment"
  final override val refField = "@id"
  final override val manyRefs = false

  // Audit is a magic, undocumented field in the ENCODE metadata.
  // Requesting frame=audit forces it to be generated.
  final override val searchFrame = "audit"

  final override def transformMetadata(
    experimentJson: JsonObject
  ): Stream[Pure, JsonObject] =
    Stream
      .emit(experimentJson("audit").flatMap(_.asObject))
      .unNone
      .flatMap { auditObj =>
        Stream.emits(auditObj.values.flatMap(_.asArray).toSeq)
      }
      .flatMap(Stream.emits(_))
      .map(_.asObject)
      .unNone
}
