package org.broadinstitute.gdr.encode.steps.download

import better.files.File

import scala.concurrent.ExecutionContext

class GetTargets(in: File, out: File, ec: ExecutionContext)
    extends GetFromPreviousMetadataStep(in, out, ec) {

  final override val entityType = "Target"
  final override val refField = "target"
  final override val manyRefs = false
}
