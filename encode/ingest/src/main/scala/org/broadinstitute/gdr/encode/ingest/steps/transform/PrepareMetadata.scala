package org.broadinstitute.gdr.encode.ingest.steps.transform

import better.files.File
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.syntax.all._
import fs2.Stream
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class PrepareMetadata(override protected val out: File, ec: ExecutionContext)
    extends IngestStep {
  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] = {
    if (!out.isDirectory) {
      Stream.raiseError(
        new IllegalArgumentException(s"$out is not a directory")
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
      val stsManifest = out / "sts-manifest.tsv"

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
        out = joinedReplicates,
        ec
      )
      val shapeFileMetadata = new ShapeFileMetadata(rawFiles, shapedFiles, ec)
      val addFileAudits =
        new JoinAuditsToFiles(rawAudits, shapedFiles, shapedFilesWithAudits, ec)

      val joinReplicatesToFiles = new JoinReplicatesToFiles(
        extendedFileMetadata = shapedFilesWithAudits,
        extendedReplicateMetadata = joinedReplicates,
        out = fullJoinedFilesMetadata,
        ec
      )
      val cleanDonorMetadata = new CleanDonorsMetadata(
        donorMetadata = rawDonors,
        joinedFileMetadata = fullJoinedFilesMetadata,
        out = cleanedDonorsJson,
        ec
      )

      val buildManifest = new BuildStsManifest(fullJoinedFilesMetadata, stsManifest, ec)

      import IngestStep.parallelize

      val run: F[Unit] = for {
        // Merge downloaded metadata into the expected schema:
        _ <- parallelize(shapeFileMetadata, extendReplicateMetadata)
        _ <- addFileAudits.build
        _ <- joinReplicatesToFiles.build
        _ <- cleanDonorMetadata.build
        // Prep inputs to downstream:
        _ <- buildManifest.build
      } yield {
        ()
      }

      Stream.eval(run)
    }
  }
}
