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
  donors: File,
  audits: File,
  override protected val out: File
) extends IngestStep {
  import MergeFilesMetadata._

  override def process[F[_]: Effect]: Stream[F, Unit] = {
    val tableStream = Stream(
      replicates -> ReplicateFields,
      experiments -> ExperimentFields,
      targets -> TargetFields,
      libraries -> LibraryFields,
      labs -> LabFields,
      samples -> BiosampleFields,
      donors -> DonorFields
    ).evalMap((lookupTable[F] _).tupled)
      .fold(Map.empty[String, JsonObject])(_ ++ _)

    val auditStream = Stream
      .eval(auditMessagesToLevels[F])

    tableStream
      .zip(auditStream)
      .flatMap {
        case (masterLookupTable, auditInfo) =>
          val join = joinWithFile[F](masterLookupTable) _

          IngestStep
            .readJsonArray(files)
            .filter(isLeaf)
            .evalMap(
              join(
                ExtendBamMetadata.ReplicateRefsPrefix,
                ReplicatePrefix,
                ReplicateFields
              )
            )
            .evalMap(join(ReplicatePrefix, ExperimentPrefix, ExperimentFields))
            .evalMap(join(ReplicatePrefix, LibraryPrefix, LibraryFields))
            .evalMap(join(ExperimentPrefix, TargetPrefix, TargetFields))
            .evalMap(join(LibraryPrefix, BiosamplePrefix, BiosampleFields))
            .evalMap(join(LibraryPrefix, LabPrefix, LabFields))
            .evalMap(join(BiosamplePrefix, DonorPrefix, DonorFields))
            .map(linkAudits(auditInfo))
      }
      .to(IngestStep.writeJsonArray(out))
  }

  private def isLeaf(file: JsonObject): Boolean = {
    val keepFile = for {
      status <- file("status").flatMap(_.asString)
      format <- file("file_format").flatMap(_.asString)
      typ <- file("output_type").flatMap(_.asString)
    } yield {
      status.equals("released") && FormatTypeWhitelist.contains(format -> typ)
    }

    keepFile.getOrElse(false)
  }

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

  private def auditMessagesToLevels[F[_]: Sync]: F[List[(String, Int)]] =
    IngestStep
      .readJsonArray(audits)
      .map { js =>
        for {
          level <- js("level").flatMap(_.as[Int].toOption)
          detail <- js("detail").flatMap(_.asString)
        } yield {
          List(detail -> level)
        }
      }
      .unNone
      .compile
      .foldMonoid

  private def joinWithFile[F[_]: Sync](table: Map[String, JsonObject])(
    prevPrefix: String,
    prefix: String,
    collectFields: Set[String]
  )(file: JsonObject): F[JsonObject] = {
    val fkField = joinedName(prefix, prevPrefix)
    val accumulatedFields = for {
      fkJson <- file(fkField)
      fks <- fkJson.as[List[String]].toOption
    } yield {
      fks.foldMap[Map[String, Set[Json]]] { fk =>
        table.get(fk).fold(Map.empty[String, Set[Json]]) { toJoin =>
          collectFields.map { f =>
            joinedName(f, prefix) -> toJoin(f).fold(Set.empty[Json])(Set(_))
          }.toMap
        }
      }
    }

    Sync[F]
      .fromOption(
        accumulatedFields,
        new IllegalStateException(s"No data found for field '$fkField' in $file")
      )
      .map { fields =>
        JsonObject.fromMap(file.toMap ++ fields.mapValues(_.asJson))
      }
  }

  private def linkAudits(messagesToLevels: List[(String, Int)])(
    mergedJson: JsonObject
  ): JsonObject = {
    val markers = auditMarkers(mergedJson)
    val matchingAudits = messagesToLevels.filter {
      case (message, _) => markers.exists(message.contains)
    }

    val maxAuditLevel =
      if (matchingAudits.isEmpty) 0 else matchingAudits.maxBy(_._2)._2

    mergedJson.add(AuditColorField, AuditColors(maxAuditLevel))
  }

  private def auditMarkers(mergedJson: JsonObject): Iterable[String] = {
    val fileId = mergedJson("accession").flatMap(_.asString)
    val experimentIds = mergedJson(joinedName("accession", ExperimentPrefix))
      .flatMap(_.asArray.map(_.flatMap(_.asString)))
    val replicateIds = mergedJson(joinedName("uuid", ReplicatePrefix))
      .flatMap(_.asArray.map(_.flatMap(_.asString)))

    Iterable.concat(
      fileId,
      experimentIds.toIterable.flatten,
      replicateIds.toIterable.flatten
    )
  }
}

object MergeFilesMetadata {

  val FormatTypeWhitelist = Set(
    "bam" -> "unfiltered alignments",
    "bigBed" -> "peaks",
    "bigWig" -> "fold change over control"
  )

  val AuditColors = Map(
    0 -> "white",
    30 -> "white",
    40 -> "yellow",
    50 -> "orange",
    60 -> "red"
  ).mapValues(_.asJson)

  val AuditColorField = "audit_color"

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

  val BiosampleFields =
    Set("biosample_term_id", "biosample_term_name", "biosample_type", "donor")

  val DonorPrefix = "donor"
  val DonorFields = Set("accession")

  val JoinedSuffix = "_list"

  def joinedName(
    fieldName: String,
    joinedPrefix: String,
    withSuffix: Boolean = true
  ): String =
    s"${joinedPrefix}__$fieldName${if (withSuffix) JoinedSuffix else ""}"
}
