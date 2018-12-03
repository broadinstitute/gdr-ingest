package org.broadinstitute.gdr.encode.explorer.export

import org.broadinstitute.gdr.encode.explorer.fields.FieldFilter

case class ExportRequest(cohortName: Option[String], filter: FieldFilter.Filters)
