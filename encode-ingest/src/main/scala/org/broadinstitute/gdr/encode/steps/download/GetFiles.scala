package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import fs2.Scheduler

import scala.concurrent.ExecutionContext

class GetFiles(in: File, out: File)(implicit ec: ExecutionContext, s: Scheduler)
    extends GetFromPreviousMetadataStep(in, out) {

  final override val entityType = "File"
  final override val refField = "files"
  final override val manyRefs = true
}
