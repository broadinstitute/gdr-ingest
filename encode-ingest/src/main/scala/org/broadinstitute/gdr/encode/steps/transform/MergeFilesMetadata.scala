package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class MergeFilesMetadata(
  files: File,
  replicates: File,
  experiments: File,
  targets: File,
  libraries: File,
  labs: File,
  samples: File,
  override protected val out: File
) extends IngestStep {
  import MergeFilesMetadata._

  override def process[F[_]: Effect]: Stream[F, Unit] =
    Stream(
      replicates -> ReplicateFields,
      experiments -> ExperimentFields,
      targets -> TargetFields,
      libraries -> LibraryFields,
      labs -> LabFields,
      samples -> BiosampleFields
    ).evalMap((lookupTable[F] _).tupled)
      .fold(Map.empty[String, JsonObject])(_ ++ _)
      .flatMap { masterLookupTable =>
        val join = joinWithFile[F](masterLookupTable) _

        IngestStep
          .readJsonArray(files)
          .evalMap(
            join(
              CollapseFileMetadata.ReplicateRefsField,
              ReplicateFields,
              Some("replicate")
            )
          )
          .evalMap(join("replicate_experiment", ExperimentFields, Some("experiment")))
          .evalMap(join("replicate_library", LibraryFields, Some("library")))
          .evalMap(join("experiment_target", TargetFields, Some("target")))
          .evalMap(join("library_biosample", BiosampleFields, None))
          .evalMap(join("library_lab", LabFields, Some("lab")))
      }
      .map(_.filterKeys(FinalFields.contains))
      .map(stripControls)
      .evalMap(flattenSingletons[F])
      .map(renameFields)
      .to(IngestStep.writeJsonArray(out))

  private def lookupTable[F[_]: Sync](
    metadata: File,
    keepFields: Set[String]
  ): F[Map[String, JsonObject]] =
    IngestStep
      .readJsonArray(metadata)
      .map { js =>
        for {
          id <- js("@id")
          idStr <- id.asString
        } yield {
          idStr -> js.filterKeys(keepFields.contains)
        }
      }
      .unNone
      .compile
      .fold(Map.empty[String, JsonObject])(_ + _)

  private def joinWithFile[F[_]: Sync](table: Map[String, JsonObject])(
    fkField: String,
    collectFields: Set[String],
    collectionPrefix: Option[String]
  )(file: JsonObject): F[JsonObject] = {
    val accumulatedFields = for {
      fkJson <- file(fkField)
      fks <- fkJson.as[List[String]].toOption
    } yield {
      fks.foldMap[Map[String, Set[Json]]] { fk =>
        val toJoin = table(fk)
        collectFields.map { f =>
          s"${collectionPrefix.fold("")(_ + "_")}$f" -> toJoin(f)
            .fold(Set.empty[Json])(Set(_))
        }.toMap
      }
    }

    Sync[F]
      .fromOption(
        accumulatedFields,
        new IllegalStateException(s"No data found for field '$fkField'")
      )
      .map { fields =>
        JsonObject.fromMap(file.toMap ++ fields.mapValues(_.asJson))
      }
  }

  private def stripControls(mergedFile: JsonObject): JsonObject = {
    val strippedTargets = for {
      labelJson <- mergedFile("target_label")
      labels <- labelJson.as[Seq[String]].toOption
    } yield {
      if (labels.length <= 1) {
        labels
      } else {
        labels.filterNot(_.matches(".*[Cc]ontrol.*"))
      }
    }
    strippedTargets.fold(mergedFile)(ts => mergedFile.add("target_label", ts.asJson))
  }

  private def flattenSingletons[F[_]: Sync](mergedFile: JsonObject): F[JsonObject] =
    FieldsToFlatten.foldLeft(Sync[F].pure(mergedFile)) { (wrappedAcc, field) =>
      val maybeFlattened = for {
        fieldJson <- mergedFile(field)
        fieldArray <- fieldJson.asArray
        if fieldArray.length == 1
      } yield {
        field -> fieldArray.head
      }

      for {
        acc <- wrappedAcc
        flattened <- Sync[F].fromOption(
          maybeFlattened,
          new IllegalStateException(s"'$field' is not a singleton array in $mergedFile")
        )
      } yield {
        flattened +: acc
      }
    }

  private def renameFields(mergedFile: JsonObject): JsonObject =
    FieldsToRename.foldLeft(mergedFile) {
      case (acc, (oldName, newName)) =>
        acc(oldName).fold(acc)(v => acc.add(newName, v).remove(oldName))
    }
}

object MergeFilesMetadata {

  val ReplicateFields = Set("experiment", "library", "uuid")

  val ExperimentFields = Set("accession", "assay_term_name", "target")

  val TargetFields = Set("label")

  val LibraryFields = Set("accession", "biosample", "lab")

  val LabFields = Set("name")

  val BiosampleFields =
    Set("biosample_term_id", "biosample_term_name", "biosample_type", "donor")

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
    CollapseFileMetadata.ReadCountField,
    DeriveActualUris.DownloadUriField,
    "replicate_uuid",
    "experiment_accession",
    "experiment_assay_term_name",
    "target_label",
    "library_accession",
    "lab_name",
    "biosample_term_id",
    "biosample_term_name",
    "biosample_type",
    "donor"
  )

  val FieldsToFlatten = Set(
    "biosample_term_name",
    "biosample_type",
    "biosample_term_id",
    "experiment_assay_term_name",
    "target_label"
  )

  val FieldsToRename = Set(
    "accession" -> "entity:sample_id",
    "biosample_term_name" -> "cell_type",
    "experiment_assay_term_name" -> "assay_term_name",
    "target_label" -> "target"
  )
}
