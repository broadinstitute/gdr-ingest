package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class CleanupFilesMetadata(mergedFiles: File, override protected val out: File)
    extends IngestStep {
  import CleanupFilesMetadata._

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
    IngestStep
      .readJsonArray(mergedFiles)
      .map(stripControls)
      .evalMap(flattenSingletons[F])
      .map(renameFields)
      .map(_.filterKeys(FinalFields.contains))
      .to(IngestStep.writeJsonArray(out))

  private def stripControls(mergedFile: JsonObject): JsonObject = {
    val strippedTargets = for {
      labelJson <- mergedFile(SuffixedLabel)
      labels <- labelJson.as[Seq[String]].toOption
    } yield {
      if (labels.length <= 1) {
        labels
      } else {
        labels.filterNot(_.matches(".*[Cc]ontrol.*"))
      }
    }
    strippedTargets.fold(mergedFile)(ts => mergedFile.add(SuffixedLabel, ts.asJson))
  }

  private def flattenSingletons[F[_]: Sync](mergedFile: JsonObject): F[JsonObject] =
    FieldsToFlatten.foldLeft(Sync[F].pure(mergedFile)) { (wrappedAcc, field) =>
      val listField = s"$field${MergeFilesMetadata.JoinedSuffix}"
      val maybeFlattened = for {
        fieldJson <- mergedFile(listField)
        fieldArray <- fieldJson.asArray
        if fieldArray.length == 1
      } yield {
        field -> fieldArray.head
      }

      for {
        acc <- wrappedAcc
        flattened <- Sync[F].fromOption(
          maybeFlattened,
          new IllegalStateException(
            s"'$listField' is not a singleton array in $mergedFile"
          )
        )
      } yield {
        (flattened +: acc).remove(listField)
      }
    }

  private def renameFields(mergedFile: JsonObject): JsonObject =
    FieldsToRename.foldLeft(mergedFile) {
      case (acc, (oldName, newName)) =>
        acc(oldName).fold(acc)(v => acc.add(newName, v).remove(oldName))
    }
}

object CleanupFilesMetadata {
  import MergeFilesMetadata._

  val AssayField = joinedName("assay_term_name", ExperimentPrefix, withSuffix = false)

  val CellTypeField =
    joinedName("biosample_term_name", BiosamplePrefix, withSuffix = false)

  val ExperimentAccessionField = joinedName("accession", ExperimentPrefix)

  val LabelField = joinedName("label", TargetPrefix, withSuffix = false)
  val SuffixedLabel = s"$LabelField$JoinedSuffix"

  val SampleTypeField = joinedName("biosample_type", BiosamplePrefix, withSuffix = false)

  val SampleTermField =
    joinedName("biosample_term_id", BiosamplePrefix, withSuffix = false)

  val FieldsToFlatten = Set(
    AssayField,
    CellTypeField,
    SampleTypeField,
    SampleTermField,
    LabelField
  )

  val FieldsToRename = Map(
    "accession" -> "file_accession",
    CellTypeField -> "cell_type",
    AssayField -> "assay_term_name",
    LabelField -> "target"
  )

  val FinalFields = Set(
    // Direct from downloaded metadata:
    "accession",
    "assembly",
    "controlled_by",
    "derived_from",
    "file_format",
    "file_size",
    "file_type",
    "md5sum",
    "output_type",
    // Derived from processing steps:
    ExtendBamMetadata.DerivedFromExperimentField,
    ExtendBamMetadata.DerivedFromReferenceField,
    ExtendBamMetadata.PercentAlignedField,
    ExtendBamMetadata.PercentDupsField,
    ExtendBamMetadata.ReadCountField,
    ExtendBamMetadata.ReadLengthField,
    ExtendBamMetadata.RunTypeField,
    DeriveActualUris.DownloadUriField,
    // Joined into file records from other metadata:
    joinedName("accession", DonorPrefix),
    ExperimentAccessionField,
    joinedName("accession", LibraryPrefix),
    joinedName("name", LabPrefix),
    joinedName("uuid", ReplicatePrefix)
  ).union(FieldsToFlatten).union(FieldsToRename.values.toSet)
}
