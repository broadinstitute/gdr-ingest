package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import cats.effect.{Effect, Sync}
import fs2.{Scheduler, Stream}
import io.circe.JsonObject
import org.broadinstitute.gdr.encode.client.EncodeClient
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/** Ingest step responsible for pulling raw metadata for a specific entity type from the ENCODE API. */
abstract class GetMetadataStep(override protected val out: File)(
  implicit ec: ExecutionContext,
  s: Scheduler
) extends IngestStep {

  final override def process[F[_]: Effect]: Stream[F, Unit] =
    EncodeClient
      .stream[F]
      .flatMap(pullMetadata[F])
      .map(transformMetadata)
      .to(IngestStep.writeJsonArray(out))

  /** Transform a downloaded entity before it is written to disk. */
  def transformMetadata(metadata: JsonObject): JsonObject = metadata

  /** String ID for the entity type this step will query. */
  def entityType: String

  /** Whether or not queries should include a "status"="released" filter. */
  def checkReleased: Boolean = true

  /**
    * Scope of metadata to retrieve from ENCODE.
    *
    * "object" will return normalized-(ish) data with references to other entities.
    * "embedded" will return fully denormalized data.
    */
  def searchFrame: String = "object"

  /** Query parameters to pass in the call to the ENCODE search API. */
  def searchParams[F[_]: Sync]: Stream[F, List[(String, String)]]

  final private def pullMetadata[F[_]: Effect](
    client: EncodeClient[F]
  ): Stream[F, JsonObject] = {
    val commonParams = if (checkReleased) {
      List("status" -> "released", "frame" -> searchFrame)
    } else {
      List("frame" -> searchFrame)
    }

    searchParams.map { params =>
      client.search(entityType, commonParams ::: params)
    }.join(EncodeClient.Parallelism)
  }
}
