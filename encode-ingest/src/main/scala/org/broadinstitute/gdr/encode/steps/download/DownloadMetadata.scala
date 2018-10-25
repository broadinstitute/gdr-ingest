package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import cats.effect.Effect
import cats.implicits._
import fs2.{Scheduler, Stream}
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class DownloadMetadata(override protected val out: File)(
  implicit ec: ExecutionContext,
  s: Scheduler
) extends IngestStep {

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
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
      val rawLabs = out / "labs.json"
      val rawLibraries = out / "libraries.json"
      val rawReplicates = out / "replicates.json"
      val rawTargets = out / "targets.json"

      // FIXME: Implicit dependencies between steps would be better made explict.

      val getExperiments = new GetExperiments(rawExperiments)
      val getReplicates = new GetReplicates(rawExperiments, rawReplicates)
      val getFiles = new GetFiles(rawExperiments, rawFiles)
      val getAudits = new GetAudits(rawFiles, rawAudits)
      val getTargets = new GetTargets(rawExperiments, rawTargets)
      val getLibraries = new GetLibraries(rawReplicates, rawLibraries)
      val getLabs = new GetLabs(rawLibraries, rawLabs)
      val getSamples = new GetBiosamples(rawLibraries, rawSamples)
      val getDonors = new GetDonors(rawSamples, rawDonors)

      import IngestStep.parallelize

      val run: F[Unit] = for {
        _ <- getExperiments.build
        _ <- parallelize(getReplicates, getFiles, getTargets)
        _ <- parallelize(getAudits, getLibraries)
        _ <- parallelize(getLabs, getSamples)
        _ <- getDonors.build
      } yield {
        ()
      }

      Stream.eval(run)
    }
}
