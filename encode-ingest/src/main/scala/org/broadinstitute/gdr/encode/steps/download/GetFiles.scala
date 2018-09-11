package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import cats.effect.Sync
import fs2.{Pipe, Stream}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GetFiles(in: File, out: File)(implicit ec: ExecutionContext)
    extends GetFromPreviousMetadataStep[Seq[String]](in, out) {

  final override val entityType = "File"
  final override val refField = "files"
  final override def refValueStream[F[_]](refValue: Seq[String]): fs2.Stream[F, String] =
    Stream.emits(refValue)
  final override def filterRefs[F[_]]: Pipe[F, String, String] = identity

  final override def searchParams[F[_]: Sync]: Stream[F, List[(String, String)]] =
    super.searchParams.map(("status" -> "released") :: _)
}
