package org.broadinstitute.gdr.encode.explorer

import org.broadinstitute.gdr.encode.explorer.count.CountController
import org.broadinstitute.gdr.encode.explorer.dataset.DatasetController
import org.broadinstitute.gdr.encode.explorer.export.ExportController
import org.broadinstitute.gdr.encode.explorer.facets.FacetsController
import org.broadinstitute.gdr.encode.explorer.fields.FieldConfig

/** Convenience wrapper around all components backing the explorer's web API. */
case class ExplorerApp(
  fields: List[FieldConfig],
  datasetController: DatasetController,
  facetsController: FacetsController,
  countController: CountController,
  exportController: ExportController
)
