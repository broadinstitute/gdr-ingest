package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import fs2.{Pipe, Stream}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GetReplicates(in: File, out: File)(implicit ec: ExecutionContext)
    extends GetFromPreviousMetadataStep[Seq[String]](in, out) {

  final override val entityType = "Replicate"
  final override val refField = "replicates"
  final override def refValueStream[F[_]](refValue: Seq[String]): fs2.Stream[F, String] =
    Stream.emits(refValue)
  final override def filterRefs[F[_]]: Pipe[F, String, String] =
    GetMetadataStep.uniquePipe[F]
}
