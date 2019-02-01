package org.broadinstitute.gdr.encode.ingest.steps.transform

import better.files.File
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.ingest.client.EncodeClient
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/**
  * Ingest step which cleans / transforms raw donor metadata from ENCODE
  * to match the schema expected by the Data Explorer API.
  *
  * @param donorMetadata path to raw human-donors metadata from the 'download-metadata' step
  * @param joinedFileMetadata path to output file from the [[JoinReplicatesToFiles]] step
  * @param out path to output file where cleaned JSON should be written
  * @param ec execution context which should run blocking I/O operations
  */
class CleanDonorsMetadata(
  donorMetadata: File,
  joinedFileMetadata: File,
  override protected val out: File,
  ec: ExecutionContext
) extends IngestStep {

  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] =
    donorAccessions[F].flatMap { accessionsToKeep =>
      IngestStep
        .readJsonArray(ec)(donorMetadata)
        .map { donor =>
          donor(JoinReplicateMetadata.DonorAccessionField)
            .flatMap(_.asString)
            .map(_ -> donor)
        }
        .unNone
        .collect {
          case (id, donor) if accessionsToKeep.contains(id) =>
            donor
              .filterKeys(CleanDonorsMetadata.RetainedFields.contains)
              .add("more_info", (EncodeClient.EncodeUri / id).toString.asJson)
        }
        .map(removeUnknowns)
        .map(normalizeAges)
        .through(
          IngestStep.renameFields(
            Map(
              JoinReplicateMetadata.DonorAccessionField -> CleanDonorsMetadata.DonorIdField
            )
          )
        )
        .through(IngestStep.writeJsonArray(ec)(out))
    }

  /**
    * Build a stream which will collect all the donor IDs referenced in the cleaned file metadata.
    *
    * Emits a single element.
    */
  private def donorAccessions[F[_]: Sync: ContextShift]: Stream[F, Set[String]] =
    IngestStep
      .readJsonArray(ec)(joinedFileMetadata)
      .map { file =>
        for {
          donorJson <- file(JoinReplicateMetadata.DonorIdField)
          accessions <- donorJson.as[Set[String]].toOption
        } yield accessions
      }
      .unNone
      .foldMonoid

  /**
    * Ensure the age value in the given donors JSON is a valid number.
    *
    * Some ages are given as ranges (10-12) in ENCODE. We replace these values
    * with the midpoint of the range.
    */
  private def normalizeAges(js: JsonObject): JsonObject = {
    import CleanDonorsMetadata.AgeField

    val maybeAge = js(AgeField).flatMap(_.asString)

    maybeAge.fold(js) { age =>
      val rangeIdx = age.indexOf('-')
      val normalized = if (rangeIdx == -1) {
        age
      } else {
        val (low, high) = (age.take(rangeIdx).toLong, age.drop(rangeIdx + 1).toLong)
        ((low + high) / 2).toString
      }
      js.add(AgeField, normalized.asJson)
    }
  }

  /**
    * Remove all values of 'unknown' from the given JSON.
    *
    * 'unknown' means the same thing as `null` in our use-case, and `null`
    * plays much more nicely with type-checking both in Scala and in the DB.
    */
  private def removeUnknowns(js: JsonObject): JsonObject =
    js.filter { case (_, value) => value != CleanDonorsMetadata.Unknown }
}

object CleanDonorsMetadata {

  val DonorIdField = "donor_id"
  val AgeField = "age"

  val Unknown = "unknown".asJson

  val RetainedFields =
    Set(
      JoinReplicateMetadata.DonorAccessionField,
      AgeField,
      "age_units",
      "health_status",
      "sex"
    )
}
