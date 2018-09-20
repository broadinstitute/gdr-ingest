package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import cats.effect.Sync
import fs2.{Pipe, Scheduler, Stream}
import io.circe.Decoder
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

abstract class GetFromPreviousMetadataStep[R: Decoder](in: File, out: File)(
  implicit ec: ExecutionContext,
  s: Scheduler
) extends GetMetadataStep(out) {

  final override def searchParams[F[_]: Sync]: Stream[F, List[(String, String)]] =
    IngestStep
      .readJsonArray(in)
      .flatMap { obj =>
        val refs = for {
          refJson <- obj(refField).toRight(
            new IllegalStateException(
              s"Expected field $refField not found in object $obj"
            )
          )
          refValues <- refJson.as[R]
        } yield {
          refValues
        }

        refs.fold(Stream.raiseError, refValueStream).covary[F]
      }
      .through(filterRefs)
      .segmentN(100)
      .map { refs =>
        refs
          .fold(List.empty[(String, String)])((acc, ref) => ("@id" -> ref) :: acc)
          .force
          .run
          ._2
      }

  def filterRefs[F[_]]: Pipe[F, String, String]
  def refField: String
  def refValueStream[F[_]](refValue: R): Stream[F, String]
}
