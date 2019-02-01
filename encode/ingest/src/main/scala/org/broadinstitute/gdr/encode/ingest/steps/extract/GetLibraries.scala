package org.broadinstitute.gdr.encode.ingest.steps.extract

import better.files.File

import scala.concurrent.ExecutionContext

class GetLibraries(in: File, out: File, ec: ExecutionContext)
    extends GetFromPreviousMetadataStep(in, out, ec) {

  final override val entityType = "Library"
  final override val refField = "library"
  final override val manyRefs = false
}
