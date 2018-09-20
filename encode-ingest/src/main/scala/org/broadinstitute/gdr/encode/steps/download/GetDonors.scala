package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import fs2.{Pipe, Scheduler, Stream}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GetDonors(in: File, out: File)(implicit ec: ExecutionContext, s: Scheduler)
    extends GetFromPreviousMetadataStep[String](in, out) {

  final override val entityType = "HumanDonor"
  final override val refField = "donor"

  final override def refValueStream[F[_]](refValue: String): Stream[F, String] =
    Stream.emit(refValue)
  final override def filterRefs[F[_]]: Pipe[F, String, String] =
    GetMetadataStep.uniquePipe[F]
}
