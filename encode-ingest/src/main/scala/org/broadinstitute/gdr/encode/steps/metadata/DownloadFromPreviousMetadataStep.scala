package org.broadinstitute.gdr.encode.steps.metadata

import better.files.File
import cats.effect.Sync
import fs2.{Pipe, Stream}
import io.circe.Decoder

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

abstract class DownloadFromPreviousMetadataStep[R: Decoder](in: File, out: File)(
  implicit ec: ExecutionContext
) extends DownloadMetadataStep(out) {

  final override def searchParams[F[_]: Sync]: Stream[F, List[(String, String)]] =
    fs2.io.file
      .readAll(in.path, 8192)
      .through(io.circe.fs2.byteArrayParser)
      .flatMap { json =>
        json.hcursor.get[R](refField).fold(Stream.raiseError, refValueStream).covary[F]
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
