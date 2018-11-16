package org.broadinstitute.gdr.encode.ingest.steps.download

import better.files.File

import scala.concurrent.ExecutionContext

class GetLabs(in: File, out: File, ec: ExecutionContext)
    extends GetFromPreviousMetadataStep(in, out, ec) {

  final override val entityType = "Lab"
  final override val refField = "lab"
  final override val manyRefs = false
}
