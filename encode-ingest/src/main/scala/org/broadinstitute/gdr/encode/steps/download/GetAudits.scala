package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import cats.effect.Effect
import fs2.{Pipe, Pure, Scheduler, Stream}
import io.circe.JsonObject
import org.broadinstitute.gdr.encode.client.EncodeClient

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GetAudits(in: File, out: File)(implicit ec: ExecutionContext, s: Scheduler)
    extends GetFromPreviousMetadataStep[String](in, out) {

  final override val entityType = "Experiment"
  final override val searchFrame = "audit"
  final override val refField = "@id"

  final override def refValueStream[F[_]](refValue: String): Stream[F, String] =
    Stream.emit(refValue)
  final override def filterRefs[F[_]]: Pipe[F, String, String] = identity

  final override def pullMetadata[F[_]: Effect](
    client: EncodeClient[F]
  ): Stream[F, JsonObject] = super.pullMetadata(client).flatMap(extractAudits)

  private def extractAudits(
    experimentJson: JsonObject
  ): Stream[Pure, JsonObject] =
    Stream
      .emit(experimentJson("audit").flatMap(_.asObject))
      .unNone
      .flatMap { auditObj =>
        Stream.emits(auditObj.values.flatMap(_.asArray).toSeq)
      }
      .flatMap(Stream.emits(_))
      .map(_.asObject)
      .unNone
}
