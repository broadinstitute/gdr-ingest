package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.Effect
import cats.implicits._
import fs2.{Scheduler, Stream}
import org.broadinstitute.gdr.encode.steps.IngestStep
import org.broadinstitute.gdr.encode.steps.google.BuildStsManifest
import org.broadinstitute.gdr.encode.steps.rawls.BuildRawlsJsons

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class PrepareMetadata(override protected val out: File)(
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

      val stsManifest = out / "sts-manifest.tsv"
      val rawlsOut = out / "rawls-inputs"

      // FIXME: Implicit dependencies between steps would be better made explict.

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

      val buildManifest = new BuildStsManifest(filesWithUris, stsManifest)
      val buildRawlsJsons = new BuildRawlsJsons(
        filesWithUris,
        cleanedDonorsJson,
        "broad-gdr-encode-storage",
        rawlsOut
      )

      import IngestStep.parallelize

      val run: F[Unit] = for {
        // Merge downloaded metadata into the expected schema:
        _ <- parallelize(shapeFileMetadata, extendReplicateMetadata)
        _ <- addFileAudits.build
        _ <- joinReplicatesToFiles.build
        _ <- cleanDonorMetadata.build
        // Find actual URIs for raw files:
        _ <- deriveUris.build
        // Prep inputs to downstream:
        _ <- parallelize(buildManifest, buildRawlsJsons)
      } yield {
        ()
      }

      Stream.eval(run)
    }
  }
}
