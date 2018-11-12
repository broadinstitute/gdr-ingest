package org.broadinstitute.gdr.encode.clp

import better.files.File
import org.broadinstitute.gdr.encode.steps.IngestStep
import org.broadinstitute.gdr.encode.steps.download.{DownloadMetadata => DownloadStep}
import org.broadinstitute.gdr.encode.steps.rawls.BuildRawlsJsons
import org.broadinstitute.gdr.encode.steps.transform.{PrepareMetadata => PrepareStep}

import scala.concurrent.ExecutionContext

sealed trait IngestCommand {
  def step(blockingEc: ExecutionContext): IngestStep
}

object IngestCommand {
  case class DownloadMetadata(outputDir: File) extends IngestCommand {
    override def step(blockingEc: ExecutionContext) =
      new DownloadStep(outputDir, blockingEc)
  }
  case class PrepareMetadata(downloadDir: File) extends IngestCommand {
    override def step(blockingEc: ExecutionContext) =
      new PrepareStep(downloadDir, blockingEc)
  }
  case class GenerateRawlsJson(
    filesJson: File,
    donorsJson: File,
    transferBucket: String,
    outputDir: File
  ) extends IngestCommand {
    override def step(blockingEc: ExecutionContext) =
      new BuildRawlsJsons(filesJson, donorsJson, transferBucket, outputDir, blockingEc)
  }
}
