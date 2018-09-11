package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import fs2.{Pipe, Stream}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GetAudits(in: File, out: File)(implicit ec: ExecutionContext)
    extends GetFromPreviousMetadataStep[String](in, out) {

  final override val entityType = "Experiment"
  final override val searchFrame = "audit"
  final override val refField = "@id"
  final override def refValueStream[F[_]](refValue: String): Stream[F, String] =
    Stream.emit(refValue)
  final override def filterRefs[F[_]]: Pipe[F, String, String] = identity
}
