package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import cats.effect.Effect
import cats.implicits._
import fs2.Stream
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GetAllMetadata(outDir: File)(implicit ec: ExecutionContext) extends IngestStep {
  override def process[F[_]: Effect]: Stream[F, Unit] = {
    if (!outDir.isDirectory) {
      Stream.raiseError(
        new IllegalArgumentException(
          s"Download must be pointed at a directory, $outDir is not a directory"
        )
      )
    } else {
      val experimentsOut = outDir / "experiments.json"
      val replicatesOut = outDir / "replicates.json"
      val librariesOut = outDir / "libraries.json"
      val samplesOut = outDir / "biosamples.json"

      val getExperiments = new GetExperiments(experimentsOut)
      val getAudits = new GetAudits(experimentsOut, outDir / "audits.json")
      val getReplicates = new GetReplicates(experimentsOut, replicatesOut)
      val getFiles = new GetFiles(experimentsOut, outDir / "files.json")
      val getTargets = new GetTargets(experimentsOut, outDir / "targets.json")
      val getLibraries = new GetLibraries(replicatesOut, librariesOut)
      val getLabs = new GetLabs(librariesOut, outDir / "labs.json")
      val getSamples = new GetBiosamples(librariesOut, samplesOut)
      val getDonors = new GetDonors(samplesOut, outDir / "donors.json")

      val download = for {
        _ <- getExperiments.build[F]
        _ <- Stream(getAudits, getReplicates, getFiles, getTargets)
          .map(_.process[F])
          .joinUnbounded
          .compile
          .drain
        _ <- getLibraries.build[F]
        _ <- Stream(getLabs, getSamples).map(_.process[F]).joinUnbounded.compile.drain
        _ <- getDonors.build[F]
      } yield {
        ()
      }

      Stream.eval(download)
    }
  }
}
