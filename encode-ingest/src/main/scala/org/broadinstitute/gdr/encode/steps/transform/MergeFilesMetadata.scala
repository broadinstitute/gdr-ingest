package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.Json
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
      .fold(Map.empty[String, Json])(_ ++ _)
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
      .map(_.mapObject(_.filterKeys(FinalFields.contains)))
      .to(IngestStep.writeJsonArray(out))

  private def lookupTable[F[_]: Sync](
    metadata: File,
    keepFields: Set[String]
  ): F[Map[String, Json]] =
    IngestStep
      .readJsonArray(metadata)
      .map { js =>
        js.hcursor
          .get[String]("@id")
          .map(_ -> js.mapObject(_.filterKeys(keepFields.contains)))
      }
      .rethrow
      .compile
      .fold(Map.empty[String, Json])(_ + _)

  private def joinWithFile[F[_]: Sync](table: Map[String, Json])(
    fkField: String,
    collectFields: Set[String],
    collectionPrefix: Option[String]
  )(file: Json): F[Json] = {
    val accumulatedFields = file.hcursor.get[List[String]](fkField).map {
      _.foldMap[Map[String, Set[String]]] { fk =>
        val cursor = table(fk).hcursor
        collectFields.map { f =>
          s"${collectionPrefix.fold("")(_ + "_")}$f" -> cursor
            .get[String](f)
            .fold(_ => Set.empty[String], Set(_))
        }.toMap
      }.asJson
    }
    Sync[F].fromEither(accumulatedFields).map(file.deepMerge)
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
}
