package org.broadinstitute.gdr.encode.steps.google

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep
import org.broadinstitute.gdr.encode.steps.transform.{
  JoinReplicateMetadata,
  JoinReplicatesToFiles
}

import scala.language.higherKinds

class BuildBqJson(
  fileMetadata: File,
  donorsMetadata: File,
  storageBucket: String,
  override protected val out: File
) extends IngestStep {

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
    Stream
      .eval(buildFileCache[F])
      .flatMap { filesPerDonor =>
        IngestStep.readJsonArray(donorsMetadata).map { donor =>
          donor(JoinReplicateMetadata.DonorIdField).map { id =>
            // "samples" because the Data Explorer special-cases it.
            donor
              .add("donor_accession", id)
              .add("samples", filesPerDonor.getOrElse(id, Vector.empty).asJson)
              .remove(JoinReplicateMetadata.DonorIdField)
          }
        }
      }
      .unNone
      .map(_.asJson.noSpaces)
      .to(IngestStep.writeLines(out))

  private def buildFileCache[F[_]: Sync]: F[Map[Json, Vector[JsonObject]]] =
    IngestStep
      .readJsonArray(fileMetadata)
      .flatMap { file =>
        val fileRecord = Gcs
          .swapUriFields(storageBucket)(file)
          .remove(JoinReplicatesToFiles.DonorFkField)

        file(JoinReplicatesToFiles.DonorFkField)
          .flatMap(_.asArray)
          .fold(Stream.empty.covaryOutput[Json])(Stream.emits)
          .foldMap(id => Map(id -> Vector(fileRecord)))
      }
      .compile
      .foldMonoid
}
