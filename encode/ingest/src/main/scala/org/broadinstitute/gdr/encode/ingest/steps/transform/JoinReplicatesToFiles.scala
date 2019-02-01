package org.broadinstitute.gdr.encode.ingest.steps.transform

import better.files.File
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.implicits._
import fs2.Stream
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class JoinReplicatesToFiles(
  extendedFileMetadata: File,
  extendedReplicateMetadata: File,
  override protected val out: File,
  ec: ExecutionContext
) extends IngestStep {
  import JoinReplicatesToFiles._

  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] =
    Stream
      .eval(IngestStep.readLookupTable(ec)(extendedReplicateMetadata))
      .flatMap { replicateTable =>
        IngestStep
          .readJsonArray(ec)(extendedFileMetadata)
          .map { fileRecord =>
            for {
              replicateIds <- fileRecord(ShapeFileMetadata.ReplicateFkField)
                .flatMap(_.as[List[String]].toOption)
              joinedJson <- joinReplicates(replicateTable)(
                fileRecord.remove(ShapeFileMetadata.ReplicateFkField),
                replicateIds
              )
            } yield {
              joinedJson
            }
          }
      }
      .unNone
      .map(flattenSingletons)
      .unNone
      .through(IngestStep.writeJsonArray(ec)(out))

  private def joinReplicates(
    replicateTable: Map[String, JsonObject]
  )(fileJson: JsonObject, replicateIds: List[String]): Option[JsonObject] = {
    val replicateInfo =
      stripControls(replicateIds.flatMap(replicateTable.get)).foldMap {
        _.toIterable.map {
          case (k, v) => k -> Set(v)
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

  private def flattenSingletons(mergedFile: JsonObject): Option[JsonObject] = {
    FieldsToFlatten.foldLeft(Option(mergedFile)) { (wrappedAcc, field) =>
      wrappedAcc.flatMap { acc =>
        mergedFile(field).fold(wrappedAcc) { fieldJson =>
          val maybeFlattened = for {
            fieldArray <- fieldJson.asArray
            if fieldArray.length == 1
          } yield {
            field -> fieldArray.head
          }

          maybeFlattened.map(_ +: acc)
        }
      }
    }
  }
}

object JoinReplicatesToFiles {
  import JoinReplicateMetadata._

  val FieldsToFlatten = Set(
    AssayField,
    CellTypeField,
    SampleTermField,
    SampleTypeField,
    TargetLabelField
  )
}
