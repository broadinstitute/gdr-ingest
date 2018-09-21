package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import cats.effect.{Effect, Sync}
import fs2.{Pipe, Scheduler, Stream}
import io.circe.JsonObject
import org.broadinstitute.gdr.encode.client.EncodeClient
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

abstract class GetMetadataStep(override protected val out: File)(
  implicit ec: ExecutionContext,
  s: Scheduler
) extends IngestStep {

  final override def process[F[_]: Effect]: Stream[F, Unit] =
    EncodeClient
      .stream[F]
      .flatMap(pullMetadata[F])
      .to(IngestStep.writeJsonArray(out))

  def pullMetadata[F[_]: Effect](client: EncodeClient[F]): Stream[F, JsonObject] =
    searchParams.map { params =>
      client.search(("type" -> entityType) :: ("frame" -> searchFrame) :: params)
    }.join(EncodeClient.Parallelism)

  def entityType: String
  def searchFrame: String = "object"
  def searchParams[F[_]: Sync]: Stream[F, List[(String, String)]]
}

object GetMetadataStep {

  def uniquePipe[F[_]]: Pipe[F, String, String] =
    _.fold(Set.empty[String])(_ + _).flatMap(s => Stream.emits(s.toSeq))
}
