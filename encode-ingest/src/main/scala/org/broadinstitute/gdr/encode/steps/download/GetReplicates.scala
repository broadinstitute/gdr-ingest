package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import fs2.Scheduler

import scala.concurrent.ExecutionContext

class GetReplicates(in: File, out: File)(implicit ec: ExecutionContext, s: Scheduler)
    extends GetFromPreviousMetadataStep(in, out) {

  final override val entityType = "Replicate"
  final override val refField = "replicates"
  final override val manyRefs = true
}
