package org.broadinstitute.gdr.encode.explorer.export

import org.broadinstitute.gdr.encode.explorer.fields.FieldFilter

/**
  * A request to export JSON entities to Terra.
  *
  * @param cohortName optional name to use as an ID for donor and file sets in the export.
  *                   No donor- or file-sets will be pushed by the export if this is `None`.
  * @param filter filters to apply to the exported JSON before sending it to Terra
  */
case class ExportRequest(cohortName: Option[String], filter: FieldFilter.Filters)
