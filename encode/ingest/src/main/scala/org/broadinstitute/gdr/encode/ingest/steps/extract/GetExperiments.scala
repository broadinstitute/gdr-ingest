package org.broadinstitute.gdr.encode.ingest.steps.extract

import better.files.File
import cats.effect.{ContextShift, Sync}
import fs2.Stream

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GetExperiments(out: File, ec: ExecutionContext) extends GetMetadataStep(out, ec) {

  override val entityType = "Experiment"
  override def searchParams[F[_]: Sync: ContextShift]: Stream[F, List[(String, String)]] =
    for {
      defaultParams <- super.searchParams[F]
      assay <- Stream.emits(GetExperiments.AssayTypesToPull).covary[F]
    } yield {
      ("assay_title" -> assay) :: defaultParams
    }
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
