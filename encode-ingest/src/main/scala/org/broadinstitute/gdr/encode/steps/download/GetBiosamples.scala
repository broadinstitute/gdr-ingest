package org.broadinstitute.gdr.encode.steps.download

import better.files.File

import scala.concurrent.ExecutionContext

class GetBiosamples(in: File, out: File, ec: ExecutionContext)
    extends GetFromPreviousMetadataStep(in, out, ec) {

  final override val entityType = "Biosample"
  final override val refField = "biosample"
  final override val manyRefs = false
}
