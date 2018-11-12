package org.broadinstitute.gdr.encode.steps.google

import better.files.File
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep
import org.broadinstitute.gdr.encode.steps.transform.JoinReplicateMetadata

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class BuildBqJson(
  fileMetadata: File,
  donorsMetadata: File,
  storageBucket: String,
  override protected val out: File,
  ec: ExecutionContext
) extends IngestStep {

  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] =
    Stream
      .eval(buildFileCache[F])
      .flatMap { filesPerDonor =>
        IngestStep.readJsonArray(ec)(donorsMetadata).map { donor =>
          donor(JoinReplicateMetadata.DonorAccessionField).map { id =>
            // "samples" because the Data Explorer special-cases it.
            donor
              .add("donor_accession", id)
              .add("samples", filesPerDonor.getOrElse(id, Vector.empty).asJson)
              .remove(JoinReplicateMetadata.DonorAccessionField)
          }
        }
      }
      .unNone
      .map(_.asJson.noSpaces)
      .to(IngestStep.writeLines(ec)(out))

  private def buildFileCache[F[_]: Sync: ContextShift]: F[Map[Json, Vector[JsonObject]]] =
    IngestStep
      .readJsonArray(ec)(fileMetadata)
      .flatMap { file =>
        val fileRecord = Gcs
          .swapUriFields(storageBucket)(file)
          .remove(JoinReplicateMetadata.DonorIdField)

        file(JoinReplicateMetadata.DonorIdField)
          .flatMap(_.asArray)
          .fold(Stream.empty.covaryOutput[Json])(Stream.emits)
          .foldMap(id => Map(id -> Vector(fileRecord)))
      }
      .compile
      .foldMonoid
}
