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

  override def process[F[_]: Effect]: Stream[F, Unit] =
    donorAccessions[F].flatMap { accessionsToKeep =>
      IngestStep
        .readJsonArray(donors)
        .filter { donor =>
          donor(MergeDonorsMetadata.IdField).exists(accessionsToKeep.contains)
        }
        .map(_.filterKeys(MergeDonorsMetadata.DonorFields.contains))
        .to(IngestStep.writeJsonArray(out))
    }

  private def donorAccessions[F[_]: Sync]: Stream[F, Set[Json]] =
    IngestStep
      .readJsonArray(mergedFiles)
      .map { file =>
        for {
          donorJson <- file(
            MergeMetadata
              .joinedName(MergeDonorsMetadata.IdField, MergeMetadata.DonorPrefix)
          )
          accessionArray <- donorJson.asArray
        } yield accessionArray.toSet[Json]
      }
      .unNone
      .fold(Set.empty[Json])(_ union _)
}

object MergeDonorsMetadata {
  val IdField = "accession"
  val DonorFields = Set(IdField, "age", "health_status", "sex")
}
