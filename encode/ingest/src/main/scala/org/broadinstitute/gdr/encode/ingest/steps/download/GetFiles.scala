package org.broadinstitute.gdr.encode.ingest.steps.download

import better.files.File

import scala.concurrent.ExecutionContext

class GetFiles(in: File, out: File, ec: ExecutionContext)
    extends GetFromPreviousMetadataStep(in, out, ec) {

  final override val entityType = "File"
  final override val refField = "files"
  final override val manyRefs = true
}
