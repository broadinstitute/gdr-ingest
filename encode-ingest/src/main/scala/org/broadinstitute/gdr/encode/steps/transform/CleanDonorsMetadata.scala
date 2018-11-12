package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.circe.syntax._
import org.broadinstitute.gdr.encode.client.EncodeClient
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

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
        .through(
          IngestStep.renameFields(
            Map(
              JoinReplicateMetadata.DonorAccessionField -> CleanDonorsMetadata.DonorIdField
            )
          )
        )
        .to(IngestStep.writeJsonArray(ec)(out))
    }

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
}

object CleanDonorsMetadata {

  val DonorIdField = "donor_id"

  val RetainedFields =
    Set(
      JoinReplicateMetadata.DonorAccessionField,
      "age",
      "age_units",
      "health_status",
      "sex"
    )
}
