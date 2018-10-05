package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.syntax._
import org.broadinstitute.gdr.encode.EncodeFields
import org.broadinstitute.gdr.encode.client.EncodeClient
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class CleanDonorsMetadata(
  donorMetadata: File,
  joinedFileMetadata: File,
  override protected val out: File
) extends IngestStep {

  override def process[F[_]: Effect]: Stream[F, Unit] =
    donorAccessions[F].flatMap { accessionsToKeep =>
      IngestStep
        .readJsonArray(donorMetadata)
        .map { donor =>
          donor(EncodeFields.DonorIdField).flatMap(_.asString).map(_ -> donor)
        }
        .unNone
        .collect {
          case (id, donor) if accessionsToKeep.contains(id) =>
            donor
              .filterKeys(CleanDonorsMetadata.RetainedFields.contains)
              .add(
                EncodeFields.EncodeUriField,
                (EncodeClient.EncodeUri / id).toString.asJson
              )
        }
        .to(IngestStep.writeJsonArray(out))
    }

  private def donorAccessions[F[_]: Sync]: Stream[F, Set[String]] = {
    import EncodeFields._

    IngestStep
      .readJsonArray(joinedFileMetadata)
      .map { file =>
        for {
          donorJson <- file(joinedName(DonorIdField, DonorPrefix, withSuffix = true))
          accessions <- donorJson.as[Set[String]].toOption
        } yield accessions
      }
      .unNone
      .foldMonoid
  }
}

object CleanDonorsMetadata {

  val RetainedFields =
    Set(EncodeFields.DonorIdField, "age", "age_units", "health_status", "sex")
}
