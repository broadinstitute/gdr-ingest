package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import cats.effect.{ContextShift, Sync}
import fs2.Stream

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GetExperiments(out: File, ec: ExecutionContext) extends GetMetadataStep(out, ec) {

  override val entityType = "Experiment"
  override def searchParams[F[_]: Sync: ContextShift]: Stream[F, List[(String, String)]] =
    Stream
      .emits(GetExperiments.AssayTypesToPull)
      .map(assay => List("assay_title" -> assay, "status" -> "released"))
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
