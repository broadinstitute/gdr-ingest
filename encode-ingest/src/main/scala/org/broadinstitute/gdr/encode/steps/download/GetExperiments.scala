package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import cats.effect.Sync
import fs2.{Scheduler, Stream}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GetExperiments(out: File)(implicit ec: ExecutionContext, s: Scheduler)
    extends GetMetadataStep(out) {

  override val entityType = "Experiment"
  override def searchParams[F[_]: Sync]: Stream[F, List[(String, String)]] =
    Stream.emit(List("assay_term_name" -> "ChIP-seq", "status" -> "released"))
}
