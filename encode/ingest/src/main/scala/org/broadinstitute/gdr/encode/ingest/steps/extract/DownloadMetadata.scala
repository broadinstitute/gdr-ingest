package org.broadinstitute.gdr.encode.ingest.steps.extract

import better.files.File
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.syntax.all._
import fs2.Stream
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class DownloadMetadata(override protected val out: File, ec: ExecutionContext)
    extends IngestStep {

  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] =
    if (!out.isDirectory) {
      Stream.raiseError(
        new IllegalArgumentException(
          s"Output must be pointed at a directory, $out is not a directory"
        )
      )
    } else {
      val rawAudits = out / "audits.json"
      val rawSamples = out / "biosamples.json"
      val rawDonors = out / "donors.json"
      val rawExperiments = out / "experiments.json"
      val rawFiles = out / "files.json"
      val rawLibraries = out / "libraries.json"
      val rawReplicates = out / "replicates.json"

      // FIXME: Implicit dependencies between steps would be better made explict.

      val getExperiments = new GetExperiments(rawExperiments, ec)
      val getReplicates = new GetReplicates(rawExperiments, rawReplicates, ec)
      val getFiles = new GetFiles(rawExperiments, rawFiles, ec)
      val getAudits = new GetAudits(rawFiles, rawAudits, ec)
      val getLibraries = new GetLibraries(rawReplicates, rawLibraries, ec)
      val getSamples = new GetBiosamples(rawLibraries, rawSamples, ec)
      val getDonors = new GetDonors(rawSamples, rawDonors, ec)

      import IngestStep.parallelize

      val run: F[Unit] = for {
        _ <- getExperiments.build
        _ <- parallelize(getReplicates, getFiles)
        _ <- parallelize(getAudits, getLibraries)
        _ <- getSamples.build
        _ <- getDonors.build
      } yield {
        ()
      }

      Stream.eval(run)
    }
}
