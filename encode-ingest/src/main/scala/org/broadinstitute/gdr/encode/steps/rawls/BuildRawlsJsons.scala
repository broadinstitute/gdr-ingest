package org.broadinstitute.gdr.encode.steps.rawls

import better.files.File
import cats.effect.{Effect, Sync}
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep
import org.broadinstitute.gdr.encode.steps.google.Gcs
import org.broadinstitute.gdr.encode.steps.transform.{
  JoinReplicateMetadata,
  ShapeFileMetadata
}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class BuildRawlsJsons(
  filesMetadata: File,
  donorsMetadata: File,
  storageBucket: String,
  override protected val out: File
)(implicit ec: ExecutionContext)
    extends IngestStep {

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
    if (!out.isDirectory) {
      Stream.raiseError(
        new IllegalArgumentException(
          s"Output must be pointed at a directory, $out is not a directory"
        )
      )
    } else {
      Stream(
        writeRawlsUpserts(
          donorsMetadata,
          "participant",
          JoinReplicateMetadata.DonorIdField
        ),
        writeRawlsUpserts(
          filesMetadata,
          "sample",
          ShapeFileMetadata.FileAccessionField,
          Gcs.swapUriFields(storageBucket)
        )
      ).joinUnbounded
    }

  private def writeRawlsUpserts[F[_]: Sync](
    metadataFile: File,
    entityType: String,
    idField: String,
    transform: JsonObject => JsonObject = identity
  ): Stream[F, Unit] =
    IngestStep
      .readJsonArray(metadataFile)
      .map(transform)
      .map(rawlsUpsert(entityType, idField))
      .unNone
      .segmentN(200)
      .zipWithIndex
      .flatMap {
        case (batch, i) =>
          Stream
            .segment(batch)
            .covary[F]
            .to(IngestStep.writeJsonArray(out / s"rawls.$entityType.$i.json"))
      }
      .drain

  private def rawlsUpsert(entityType: String, idField: String)(
    metadata: JsonObject
  ): Option[JsonObject] =
    for {
      entityName <- metadata(idField)
    } yield {
      JsonObject(
        "entityType" -> entityType.asJson,
        "name" -> entityName,
        "operations" -> metadata
          .remove(idField)
          .toIterable
          .flatMap { case (k, v) => rawlsOperations(k, v) }
          .asJson
      )
    }

  private def rawlsOperations(key: String, value: Json): Iterable[Json] =
    if (value.isArray) {
      value.asArray.toIterable.flatMap { values =>
        Json.obj("op" -> "RemoveAttribute".asJson, "attributeName" -> key.asJson) +:
          values.map { v =>
            Json.obj(
              "op" -> "AddListMember".asJson,
              "attributeListName" -> key.asJson,
              "newMember" -> v
            )
          }
      }
    } else {
      Iterable {
        Json.obj(
          "op" -> "AddUpdateAttribute".asJson,
          "attributeName" -> key.asJson,
          "addUpdateAttribute" -> value
        )
      }
    }
}
