package org.broadinstitute.gdr.encode.explorer.dataset

/**
  * Dummy wrapper around dataset information that will be queried by the UI.
  *
  * This should probably go away if we start tweaking the UI for fit our needs.
  *
  * @param datasetInfo the info that should be returned to the UI
  */
class DatasetController(val datasetInfo: DatasetResponse)

object DatasetController {

  /** The only instance of the controller that's actually used. */
  val default: DatasetController = new DatasetController(DatasetResponse(name = "ENCODE"))
}
