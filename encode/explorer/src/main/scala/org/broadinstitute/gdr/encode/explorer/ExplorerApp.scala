package org.broadinstitute.gdr.encode.explorer

import cats.effect.IO
import org.broadinstitute.gdr.encode.explorer.count.CountController
import org.broadinstitute.gdr.encode.explorer.dataset.DatasetController
import org.broadinstitute.gdr.encode.explorer.export.ExportController
import org.broadinstitute.gdr.encode.explorer.facets.FacetsController
import org.broadinstitute.gdr.encode.explorer.fields.FieldConfig

case class ExplorerApp(
  fields: List[FieldConfig],
  datasetController: DatasetController,
  facetsController: FacetsController[IO, IO.Par],
  countController: CountController[IO, IO.Par],
  exportController: ExportController[IO, IO.Par]
)
