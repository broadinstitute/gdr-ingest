package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.Json
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class CleanDonorsMetadata(
  donorMetadata: File,
  joinedFileMetadata: File,
  override protected val out: File
) extends IngestStep {
  import org.broadinstitute.gdr.encode.EncodeFields._

  override def process[F[_]: Effect]: Stream[F, Unit] =
    donorAccessions[F].flatMap { accessionsToKeep =>
      IngestStep
        .readJsonArray(donorMetadata)
        .filter { donor =>
          donor(DonorIdField).exists(accessionsToKeep.contains)
        }
        .map(_.filterKeys(DonorFields.contains))
        .to(IngestStep.writeJsonArray(out))
    }

  private def donorAccessions[F[_]: Sync]: Stream[F, Set[Json]] =
    IngestStep
      .readJsonArray(joinedFileMetadata)
      .map { file =>
        for {
          donorJson <- file(joinedName(DonorIdField, DonorPrefix, withSuffix = true))
          accessionArray <- donorJson.asArray
        } yield accessionArray.toSet[Json]
      }
      .unNone
      .foldMonoid
}
