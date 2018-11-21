package org.broadinstitute.gdr.encode.explorer.dataset

class DatasetController(val datasetInfo: DatasetResponse)

object DatasetController {
  val default: DatasetController = new DatasetController(DatasetResponse(name = "ENCODE"))
}
