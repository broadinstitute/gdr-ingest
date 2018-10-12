package org.broadinstitute.gdr.encode.steps

import better.files.File
import cats.effect.Effect
import cats.implicits._
import fs2.{Scheduler, Stream}
import org.broadinstitute.gdr.encode.steps.cromwell.BuildCromwellInputs
import org.broadinstitute.gdr.encode.steps.download._
import org.broadinstitute.gdr.encode.steps.google.BuildStsManifest
import org.broadinstitute.gdr.encode.steps.transform._

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class PrepareIngest(override protected val out: File)(
  implicit ec: ExecutionContext,
  s: Scheduler
) extends IngestStep {
  override def process[F[_]: Effect]: Stream[F, Unit] = {
    if (!out.isDirectory) {
      Stream.raiseError(
        new IllegalArgumentException(
          s"Download must be pointed at a directory, $out is not a directory"
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

      val shapedFiles = out / "files.shaped.json"
      val shapedFilesWithAudits = out / "files.shaped.with-audits.json"

      val joinedReplicates = out / "replicates.joined.json"
      val fullJoinedFilesMetadata = out / "files.joined.json"

      val cleanedDonorsJson = out / "donors.cleaned.json"

      val filesWithUris = out / "files.with-uris.json"
      val transferManifest = out / "sts-manifest.tsv"

      // FIXME: Implicit dependencies between steps would be better made explict.

      // Download metadata:
      val getExperiments = new GetExperiments(rawExperiments)
      val getReplicates = new GetReplicates(rawExperiments, rawReplicates)
      val getFiles = new GetFiles(rawExperiments, rawFiles)
      val getAudits = new GetAudits(rawFiles, rawAudits)
      val getTargets = new GetTargets(rawExperiments, rawTargets)
      val getLibraries = new GetLibraries(rawReplicates, rawLibraries)
      val getLabs = new GetLabs(rawLibraries, rawLabs)
      val getSamples = new GetBiosamples(rawLibraries, rawSamples)
      val getDonors = new GetDonors(rawSamples, rawDonors)

      // Transform & combine metadata:
      val extendReplicateMetadata = new JoinReplicateMetadata(
        replicateMetadata = rawReplicates,
        experimentMetadata = rawExperiments,
        targetMetadata = rawTargets,
        libraryMetadata = rawLibraries,
        labMetadata = rawLabs,
        sampleMetadata = rawSamples,
        donorMetadata = rawDonors,
        out = joinedReplicates
      )
      val shapeFileMetadata = new ShapeFileMetadata(rawFiles, shapedFiles)
      val addFileAudits =
        new JoinAuditsToFiles(rawAudits, shapedFiles, shapedFilesWithAudits)

      val joinReplicatesToFiles = new JoinReplicatesToFiles(
        extendedFileMetadata = shapedFilesWithAudits,
        extendedReplicateMetadata = joinedReplicates,
        out = fullJoinedFilesMetadata
      )
      val cleanDonorMetadata = new CleanDonorsMetadata(
        donorMetadata = rawDonors,
        joinedFileMetadata = fullJoinedFilesMetadata,
        out = cleanedDonorsJson
      )

      val deriveUris = new DeriveActualUris(fullJoinedFilesMetadata, filesWithUris)

      val buildTransferManifest = new BuildStsManifest(filesWithUris, transferManifest)
      val buildCromwellInputs = new BuildCromwellInputs(filesWithUris, out)

      import IngestStep.parallelize

      val run: F[Unit] = for {
        // Download the universe of raw metadata:
        _ <- getExperiments.build
        _ <- parallelize(getReplicates, getFiles, getTargets)
        _ <- parallelize(getAudits, getLibraries)
        _ <- parallelize(getLabs, getSamples)
        _ <- getDonors.build
        // Merge downloaded metadata into the expected schema:
        _ <- parallelize(shapeFileMetadata, extendReplicateMetadata)
        _ <- addFileAudits.build
        _ <- joinReplicatesToFiles.build
        _ <- cleanDonorMetadata.build
        // Find actual URIs for raw files:
        _ <- deriveUris.build
        // Generate inputs to downstream ingest processes:
        _ <- parallelize(buildTransferManifest, buildCromwellInputs)
      } yield {
        ()
      }

      Stream.eval(run)
    }
  }
}
