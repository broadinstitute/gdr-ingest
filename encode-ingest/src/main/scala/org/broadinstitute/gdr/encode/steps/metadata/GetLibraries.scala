package org.broadinstitute.gdr.encode.steps.metadata

import better.files.File
import fs2.{Pipe, Stream}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GetLibraries(in: File, out: File)(implicit ec: ExecutionContext)
    extends GetFromPreviousMetadataStep[String](in, out) {

  final override val entityType = "Library"
  final override val refField = "library"
  final override def refValueStream[F[_]](refValue: String): fs2.Stream[F, String] =
    Stream.emit(refValue)
  final override def filterRefs[F[_]]: Pipe[F, String, String] =
    GetMetadataStep.uniquePipe[F]
}
