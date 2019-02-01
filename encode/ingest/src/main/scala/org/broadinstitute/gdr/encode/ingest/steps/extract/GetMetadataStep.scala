package org.broadinstitute.gdr.encode.ingest.steps.extract

import better.files.File
import cats.effect._
import fs2.Stream
import io.circe.JsonObject
import org.broadinstitute.gdr.encode.ingest.client.EncodeClient
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/** Ingest step responsible for pulling raw metadata for a specific entity type from the ENCODE API. */
abstract class GetMetadataStep(
  override protected val out: File,
  protected val blockingEc: ExecutionContext
) extends IngestStep {

  final override def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] =
    EncodeClient
      .stream[F]
      .flatMap(pullMetadata[F])
      .map(transformMetadata)
      .through(IngestStep.writeJsonArray(blockingEc)(out))

  /** Transform a downloaded entity before it is written to disk. */
  def transformMetadata(metadata: JsonObject): JsonObject = metadata

  /** String ID for the entity type this step will query. */
  def entityType: String

  /**
    * Scope of metadata to retrieve from ENCODE.
    *
    * "object" will return normalized-(ish) data with references to other entities.
    * "embedded" will return fully denormalized data.
    */
  def searchFrame: String = "object"

  /** Query parameters to pass in the call to the ENCODE search API. */
  def searchParams[F[_]: Sync: ContextShift]: Stream[F, List[(String, String)]]

  final private def pullMetadata[F[_]: Concurrent: ContextShift](
    client: EncodeClient[F]
  ): Stream[F, JsonObject] =
    searchParams.map { params =>
      client.search(entityType, ("frame" -> searchFrame) :: params)
    }.parJoin(EncodeClient.Parallelism)
}
