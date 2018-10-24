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
    Stream
      .emits(GetExperiments.AssayTypesToPull)
      .map(assay => List("assay_title" -> assay))
}

object GetExperiments {

  val AssayTypesToPull = List(
    "ATAC-seq",
    "ChIA-PET",
    "ChIP-seq",
    "DNase-seq",
    "Hi-C",
    "microRNA-seq",
    "polyA RNA-seq",
    "RRBS",
    "total RNA-seq",
    "WGBS"
  )
}
