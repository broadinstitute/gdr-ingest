package org.broadinstitute.gdr.encode.ingest.steps.transform

import better.files.File
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import fs2.Stream
import io.circe.JsonObject
import org.broadinstitute.gdr.encode.ingest.EncodeFields
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class JoinReplicateMetadata(
  replicateMetadata: File,
  experimentMetadata: File,
  targetMetadata: File,
  libraryMetadata: File,
  labMetadata: File,
  sampleMetadata: File,
  donorMetadata: File,
  override protected val out: File,
  ec: ExecutionContext
) extends IngestStep {
  import EncodeFields._
  import JoinReplicateMetadata._

  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] =
    Stream(
      experimentMetadata,
      targetMetadata,
      libraryMetadata,
      labMetadata,
      sampleMetadata,
      donorMetadata
    ).evalMap(IngestStep.readLookupTable[F](ec)(_))
      .fold(Map.empty[String, JsonObject])(_ ++ _)
      .map(extendMetadata)
      .flatMap { join =>
        val transforms = Seq(
          join(ExperimentPrefix, ExperimentPrefix, ExperimentFields),
          join(LibraryPrefix, LibraryPrefix, LibraryFields),
          join(joinedName(TargetPrefix, ExperimentPrefix), TargetPrefix, TargetFields),
          join(
            joinedName(BiosamplePrefix, LibraryPrefix),
            BiosamplePrefix,
            BiosampleFields
          ),
          join(joinedName(LabPrefix, LibraryPrefix), LabPrefix, LabFields),
          join(
            joinedName(DonorPrefix, BiosamplePrefix),
            DonorPrefix,
            Set(DonorAccessionField)
          )
        )

        val replicates = IngestStep
          .readJsonArray(ec)(replicateMetadata)
          .map(_.filterKeys((ReplicateFields + EncodeIdField).contains))

        transforms.foldLeft(replicates)(_.map(_))
      }
      .through(IngestStep.renameFields(FieldsToRename))
      .filter(_.contains(DonorIdField))
      .to(IngestStep.writeJsonArray(ec)(out))

  /**
    * Extend JSON metadata by replacing a foreign-key field with a set of fields
    * associated with the entity pointed to by the key.
    */
  private def extendMetadata(idToMetadata: Map[String, JsonObject])(
    foreignKeyField: String,
    joinedFieldPrefix: String,
    fieldsToJoin: Set[String]
  )(metadata: JsonObject): JsonObject = {
    val foreignMetadata = for {
      foreignKey <- metadata(foreignKeyField).flatMap(_.asString)
      foreignMetadata <- idToMetadata.get(foreignKey)
    } yield {
      fieldsToJoin.flatMap { field =>
        foreignMetadata(field).map(joinedName(field, joinedFieldPrefix) -> _)
      }.toMap
    }

    foreignMetadata
      .fold(metadata)(fm => JsonObject.fromMap(metadata.toMap ++ fm))
      .remove(foreignKeyField)
  }
}

object JoinReplicateMetadata {
  import EncodeFields._

  val ReplicatePrefix = "replicate"
  val ReplicateFields = Set("uuid", "experiment", "library")

  val ExperimentPrefix = "experiment"
  val ExperimentFields = Set("accession", "assay_title", "target")

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
  val DonorAccessionField = "accession"

  val AssayField = "assay_type"
  val CellTypeField = "cell_type"
  val DonorIdField = "donor_ids"
  val SampleTermField = "biosample_term_id"
  val SampleTypeField = "biosample_type"
  val TargetLabelField = "target_of_assay"

  val FieldsToRename = Map(
    joinedName("accession", BiosamplePrefix) -> "biosamples",
    joinedName(DonorAccessionField, DonorPrefix) -> DonorIdField,
    joinedName("accession", ExperimentPrefix) -> "experiments",
    joinedName("accession", LibraryPrefix) -> "DNA_library_ids",
    joinedName("assay_title", ExperimentPrefix) -> AssayField,
    joinedName("biosample_term_id", BiosamplePrefix) -> SampleTermField,
    joinedName("biosample_term_name", BiosamplePrefix) -> CellTypeField,
    joinedName("biosample_type", BiosamplePrefix) -> SampleTypeField,
    joinedName("label", TargetPrefix) -> TargetLabelField,
    joinedName("name", LabPrefix) -> "labs_generating_data",
    "uuid" -> "replicate_id"
  )
}
