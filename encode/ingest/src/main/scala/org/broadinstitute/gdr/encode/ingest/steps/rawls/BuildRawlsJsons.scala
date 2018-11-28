package org.broadinstitute.gdr.encode.ingest.steps.rawls

import better.files.File
import cats.effect._
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.ingest.steps.{ExportTransforms, IngestStep}
import org.broadinstitute.gdr.encode.ingest.steps.transform.CleanDonorsMetadata
import org.broadinstitute.gdr.encode.ingest.steps.transform.{
  JoinReplicateMetadata,
  ShapeFileMetadata
}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class BuildRawlsJsons(
  filesMetadata: File,
  donorsMetadata: File,
  storageBucket: String,
  override protected val out: File,
  ec: ExecutionContext
) extends IngestStep {

  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] =
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
          CleanDonorsMetadata.DonorIdField
        ),
        writeRawlsUpserts(
          filesMetadata,
          "sample",
          ShapeFileMetadata.FileIdField,
          ExportTransforms.swapFileFields(storageBucket)
        )
      ).parJoinUnbounded
    }

  private def writeRawlsUpserts[F[_]: Sync: ContextShift](
    metadataFile: File,
    entityType: String,
    idField: String,
    transform: JsonObject => JsonObject = identity
  ): Stream[F, Unit] =
    IngestStep
      .readJsonArray(ec)(metadataFile)
      .map(transform)
      .map(rawlsUpsert(entityType, idField))
      .unNone
      .chunkN(200)
      .zipWithIndex
      .flatMap {
        case (batch, i) =>
          Stream
            .chunk(batch)
            .covary[F]
            .to(IngestStep.writeJsonArray(ec)(out / s"rawls.$entityType.$i.json"))
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

  private def rawlsOperations(key: String, value: Json): Iterable[Json] = {
    import BuildRawlsJsons._

    val jsonKey = key.asJson
    if (value.isArray) {
      val donorReference = key == JoinReplicateMetadata.DonorIdField
      val (createListKey, createListOp, fieldKey) = if (donorReference) {
        ("attributeListName", CreateReferenceListOp, ParticipantIdKey)
      } else {
        ("attributeName", CreateValueListOp, jsonKey)
      }

      value.asArray.toIterable.flatMap { values =>
        Vector(
          Json.obj("op" -> RemoveFieldOp, "attributeName" -> fieldKey),
          Json.obj("op" -> createListOp.asJson, createListKey -> fieldKey)
        ) ++ values.map { v =>
          val vJson = if (donorReference) {
            Json.obj("entityType" -> ParticipantType, "entityName" -> v.asJson)
          } else {
            v.asJson
          }

          Json.obj(
            "op" -> AddListMemberOp,
            "attributeListName" -> fieldKey,
            "newMember" -> vJson
          )
        }
      }
    } else {
      Iterable {
        Json.obj(
          "op" -> UpsertScalarOp,
          "attributeName" -> jsonKey,
          "addUpdateAttribute" -> value
        )
      }
    }
  }
}

object BuildRawlsJsons {
  val AddListMemberOp = "AddListMember".asJson
  val UpsertScalarOp = "AddUpdateAttribute".asJson
  val CreateReferenceListOp = "CreateAttributeEntityReferenceList".asJson
  val CreateValueListOp = "CreateAttributeValueList".asJson
  val RemoveFieldOp = "RemoveAttribute".asJson

  val ParticipantIdKey = "participant_ids".asJson
  val ParticipantType = "participant".asJson
}
