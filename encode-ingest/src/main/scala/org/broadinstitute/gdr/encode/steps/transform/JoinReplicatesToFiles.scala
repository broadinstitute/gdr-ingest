package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.Effect
import cats.implicits._
import fs2.Stream
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.EncodeFields
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class JoinReplicatesToFiles(
  extendedFileMetadata: File,
  extendedReplicateMetadata: File,
  override protected val out: File
) extends IngestStep {
  import JoinReplicatesToFiles._

  override def process[F[_]: Effect]: Stream[F, Unit] =
    Stream
      .eval(IngestStep.readLookupTable(extendedReplicateMetadata))
      .flatMap { replicateTable =>
        IngestStep
          .readJsonArray(extendedFileMetadata)
          .map { fileRecord =>
            for {
              replicateIds <- fileRecord(ShapeFileMetadata.ReplicateFkField)
                .flatMap(_.as[List[String]].toOption)
              joinedJson <- joinReplicates(replicateTable)(
                fileRecord.remove(ShapeFileMetadata.ReplicateFkField),
                replicateIds
              )
            } yield {
              joinedJson.add(FileAvailableField, shouldTransfer(joinedJson).asJson)
            }
          }
      }
      .unNone
      .map(flattenSingletons)
      .to(IngestStep.writeJsonArray(out))

  private def shouldTransfer(file: JsonObject): Boolean = {
    val keepFile = for {
      status <- file("status").flatMap(_.asString)
      format <- file("file_format").flatMap(_.asString)
      typ <- file("output_type").flatMap(_.asString)
    } yield {
      status.equals("released") &&
      FormatTypeWhitelist.contains(format -> typ)
    }

    keepFile.getOrElse(false)
  }

  private def joinReplicates(
    replicateTable: Map[String, JsonObject]
  )(fileJson: JsonObject, replicateIds: List[String]): Option[JsonObject] = {
    val replicateInfo =
      stripControls(replicateIds.flatMap(replicateTable.get)).foldMap {
        _.toIterable.map {
          case (k, v) => s"$k$JoinedSuffix" -> Set(v)
        }.toMap
      }

    if (replicateInfo.isEmpty) {
      None
    } else {
      Some(
        JsonObject.fromMap(fileJson.toMap ++ replicateInfo.mapValues(_.asJson))
      )
    }
  }

  private def stripControls(replicateRecords: List[JsonObject]): List[JsonObject] = {
    if (replicateRecords.length <= 1) {
      replicateRecords
    } else {
      replicateRecords.flatMap { record =>
        record(JoinReplicateMetadata.TargetLabelField)
          .flatMap(_.asString)
          .map(_ -> record)
      }.collect {
        case (label, record) if !label.matches(".*[Cc]ontrol.*") => record
      }
    }
  }

  private def flattenSingletons(mergedFile: JsonObject): JsonObject =
    FieldsToFlatten.foldLeft(mergedFile) { (acc, field) =>
      val listField = s"$field$JoinedSuffix"
      val maybeFlattened = for {
        fieldJson <- mergedFile(listField)
        fieldArray <- fieldJson.asArray
        if fieldArray.length == 1
      } yield {
        field -> fieldArray.head
      }

      maybeFlattened.fold(acc)(_ +: acc).remove(listField)
    }
}

object JoinReplicatesToFiles {
  import JoinReplicateMetadata._

  val JoinedSuffix = "_list"

  val FormatTypeWhitelist = Set(
    "bam" -> "unfiltered alignments",
    "bigBed" -> "peaks",
    "bigWig" -> "fold change over control"
  )

  val FileAvailableField = "gcs_file_available"

  val FieldsToFlatten = Set(
    AssayField,
    CellTypeField,
    SampleTermField,
    SampleTypeField,
    TargetLabelField
  )

  val DonorFkField = s"${EncodeFields.joinedName(DonorIdField, DonorPrefix)}$JoinedSuffix"
}
