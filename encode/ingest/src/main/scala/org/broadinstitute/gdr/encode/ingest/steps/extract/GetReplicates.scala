package org.broadinstitute.gdr.encode.ingest.steps.extract

import better.files.File

import scala.concurrent.ExecutionContext

class GetReplicates(in: File, out: File, ec: ExecutionContext)
    extends GetFromPreviousMetadataStep(in, out, ec) {

  final override val entityType = "Replicate"
  final override val refField = "replicates"
  final override val manyRefs = true
}
