package org.broadinstitute.gdr.encode.ingest.clp

import better.files.File
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep
import org.broadinstitute.gdr.encode.ingest.steps.extract.DownloadMetadata
import org.broadinstitute.gdr.encode.ingest.steps.load.ExportMetadata
import org.broadinstitute.gdr.encode.ingest.steps.transform.PrepareMetadata

import scala.concurrent.ExecutionContext

sealed trait IngestCommand {
  def step(blockingEc: ExecutionContext): IngestStep
}

object IngestCommand {
  case class Extract(outputDir: File) extends IngestCommand {
    override def step(blockingEc: ExecutionContext) =
      new DownloadMetadata(outputDir, blockingEc)
  }
  case class Transform(workDir: File) extends IngestCommand {
    override def step(blockingEc: ExecutionContext) =
      new PrepareMetadata(workDir, blockingEc)
  }
  case class Load(
    workDir: File,
    transferBucket: String
  ) extends IngestCommand {
    override def step(blockingEc: ExecutionContext): IngestStep =
      new ExportMetadata(transferBucket, workDir, blockingEc)
  }
}
