package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import fs2.Scheduler

import scala.concurrent.ExecutionContext

class GetLibraries(in: File, out: File)(implicit ec: ExecutionContext, s: Scheduler)
    extends GetFromPreviousMetadataStep(in, out) {

  final override val entityType = "Library"
  final override val refField = "library"
  final override val manyRefs = false
}
