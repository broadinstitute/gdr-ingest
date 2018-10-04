package org.broadinstitute.gdr.encode

/** Bucket for field names used while transforming raw metadata pulled from ENCODE. */
object EncodeFields {

  val EncodeIdField = "@id"

  ///////////////////////////////////////////////////

  val DerivedFromExperimentField = "derived_from_exp"
  val DerivedFromReferenceField = "derived_from_ref"
  val PercentDupsField = "percent_duplicated"
  val PercentAlignedField = "percent_aligned"
  val ReadCountField = "read_count"
  val ReadLengthField = "read_length"
  val RunTypeField = "run_type"
  val ReplicateRefsPrefix = "file"

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

  val AuditColorField = "audit_color"
  val AuditWarningsField = "warning_summary"

  //////////////////////////////////////////////////////

  val AssayField = joinedName("assay_term_name", ExperimentPrefix)

  val CellTypeField =
    joinedName("biosample_term_name", BiosamplePrefix)

  val ExperimentLinkField = joinedName("accession", ExperimentPrefix, withSuffix = true)

  val LabelField = joinedName("label", TargetPrefix)

  val FileIdField = "accession"
  val FileAccessionField = "file_accession"
  val DonorFkField = joinedName(DonorIdField, DonorPrefix)

  val ReplicateLinkField = joinedName("id", ReplicatePrefix, withSuffix = true)

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
    FileIdField -> FileAccessionField,
    CellTypeField -> "cell_type",
    AssayField -> "assay_term_name",
    LabelField -> "target",
    SampleTermField -> "biosample_term_id",
    SampleTypeField -> "biosample_type"
  )

  val FinalFileFields = Set(
    // Direct from downloaded metadata:
    "assembly",
    "file_format",
    "file_size",
    "file_type",
    "href",
    "md5sum",
    "output_type",
    // Derived from processing steps:
    AuditColorField,
    AuditWarningsField,
    DerivedFromExperimentField,
    DerivedFromReferenceField,
    PercentAlignedField,
    PercentDupsField,
    ReadCountField,
    ReadLengthField,
    RunTypeField,
    // Joined into file records from other metadata:
    DonorFkField,
    ExperimentLinkField,
    ReplicateLinkField,
    joinedName("accession", BiosamplePrefix),
    joinedName("accession", LibraryPrefix),
    joinedName("name", LabPrefix)
  ).union(FieldsToFlatten).union(FieldsToRename.values.toSet)
}
