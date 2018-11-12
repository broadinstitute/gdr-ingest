package org.broadinstitute.gdr.encode.steps.download

import better.files.File

import scala.concurrent.ExecutionContext

class GetDonors(in: File, out: File, ec: ExecutionContext)
    extends GetFromPreviousMetadataStep(in, out, ec) {

  final override val entityType = "HumanDonor"
  final override val refField = "donor"
  final override val manyRefs = false
}
