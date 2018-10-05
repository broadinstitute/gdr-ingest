package org.broadinstitute.gdr.encode

/** Bucket for field names used while transforming raw metadata pulled from ENCODE. */
object EncodeFields {

  val EncodeIdField = "@id"
  val EncodeAuditField = "audit"

  ///////////////////////////////////////////////////

  val DownloadUriField = "url"

  ///////////////////////////////////////////////////

  val JoinedSuffix = "_list"

  def joinedName(
    fieldName: String,
    joinedPrefix: String,
    withSuffix: Boolean = false
  ): String =
    s"${joinedPrefix}__$fieldName${if (withSuffix) JoinedSuffix else ""}"

  val ReplicatePrefix = "replicate"
  val ReplicateIdField = "uuid"
  val ReplicateFields = Set("uuid", "experiment", "library")

  val ExperimentPrefix = "experiment"
  val ExperimentFields = Set("accession", "assay_term_name", "target")

  val TargetPrefix = "target"
  val TargetFields = Set("label")

  val LibraryPrefix = "library"
  val LibraryFields = Set("accession", "biosample", "lab")

  val LabPrefix = "lab"
  val LabFields = Set("name")

  val BiosamplePrefix = "biosample"

  val BiosampleFields = Set(
    "accession",
    "biosample_term_id",
    "biosample_term_name",
    "biosample_type",
    "donor"
  )

  val DonorPrefix = "donor"
  val DonorIdField = "accession"
  val DonorFields = Set(DonorIdField, "age", "health_status", "sex")

  //////////////////////////////////////////////////////

  val AssayField = joinedName("assay_term_name", ExperimentPrefix)

  val CellTypeField =
    joinedName("biosample_term_name", BiosamplePrefix)

  val ExperimentLinkField = joinedName("accession", ExperimentPrefix, withSuffix = true)

  val LabelField = joinedName("label", TargetPrefix)

  val DonorFkField = joinedName(DonorIdField, DonorPrefix)

  val SampleTermField =
    joinedName("biosample_term_id", BiosamplePrefix)
  val SampleTypeField = joinedName("biosample_type", BiosamplePrefix)

  val FieldsToFlatten = Set(
    AssayField,
    CellTypeField,
    SampleTermField,
    SampleTypeField,
    LabelField
  )

  val FieldsToRename = Map(
    CellTypeField -> "cell_type",
    AssayField -> "assay_term_name",
    LabelField -> "target",
    SampleTermField -> "biosample_term_id",
    SampleTypeField -> "biosample_type"
  )
}
