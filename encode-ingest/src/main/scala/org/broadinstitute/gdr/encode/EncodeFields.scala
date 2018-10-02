package org.broadinstitute.gdr.encode

/** Bucket for field names used while transforming raw metadata pulled from ENCODE. */
object EncodeFields {

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
    withSuffix: Boolean = true
  ): String =
    s"${joinedPrefix}__$fieldName${if (withSuffix) JoinedSuffix else ""}"

  val ReplicatePrefix = "replicate"
  val ReplicateFields = Set("experiment", "library", "uuid")

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

  val AssayField = joinedName("assay_term_name", ExperimentPrefix, withSuffix = false)

  val CellTypeField =
    joinedName("biosample_term_name", BiosamplePrefix, withSuffix = false)

  val ExperimentLinkField = joinedName("accession", ExperimentPrefix)

  val LabelField = joinedName("label", TargetPrefix, withSuffix = false)
  val SuffixedLabel = s"$LabelField$JoinedSuffix"

  val FileIdField = "accession"
  val FileAccessionField = "file_accession"
  val DonorFkField = joinedName(DonorIdField, DonorPrefix)

  val ReplicateLinkField = joinedName("uuid", ReplicatePrefix)

  val SampleTermField =
    joinedName("biosample_term_id", BiosamplePrefix, withSuffix = false)
  val SampleTypeField = joinedName("biosample_type", BiosamplePrefix, withSuffix = false)

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
