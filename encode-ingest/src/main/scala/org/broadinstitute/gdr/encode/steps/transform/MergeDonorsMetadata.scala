package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import fs2.Stream
import io.circe.Json
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class MergeDonorsMetadata(
  donors: File,
  mergedFiles: File,
  override protected val out: File
) extends IngestStep {
  import org.broadinstitute.gdr.encode.EncodeFields._

  override def process[F[_]: Effect]: Stream[F, Unit] =
    donorAccessions[F].flatMap { accessionsToKeep =>
      IngestStep
        .readJsonArray(donors)
        .filter { donor =>
          donor(DonorIdField).exists(accessionsToKeep.contains)
        }
        .map(_.filterKeys(DonorFields.contains))
        .to(IngestStep.writeJsonArray(out))
    }

  private def donorAccessions[F[_]: Sync]: Stream[F, Set[Json]] =
    IngestStep
      .readJsonArray(mergedFiles)
      .map { file =>
        for {
          donorJson <- file(joinedName(DonorIdField, DonorPrefix))
          accessionArray <- donorJson.asArray
        } yield accessionArray.toSet[Json]
      }
      .unNone
      .fold(Set.empty[Json])(_ union _)
}
