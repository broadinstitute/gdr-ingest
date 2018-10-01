package org.broadinstitute.gdr.encode.steps

import better.files.File
import cats.effect.Effect
import cats.implicits._
import fs2.{Scheduler, Stream}
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
      val auditsOut = out / "audits.json"
      val biosamplesOut = out / "biosamples.json"
      val donorsOut = out / "donors.json"
      val experimentsOut = out / "experiments.json"
      val filesOut = out / "files.json"
      val labsOut = out / "labs.json"
      val librariesOut = out / "libraries.json"
      val replicatesOut = out / "replicates.json"
      val targetsOut = out / "targets.json"

      val extendedFilesOut = out / "files.extended.json"
      val mergedFilesJson = out / "files.merged.json"
      val mergedDonorsJson = out / "donors.merged.json"
      val cleanedFiles = out / "files.cleaned.json"
      val mergedWithAudits = out / "files.merged.with-audits.json"
      val filesWithUris = out / "files.with-uris.json"
      val transferManifest = out / "sts-manifest.tsv"

      // FIXME: Implicit dependencies between steps would be better made explict.

      // Download metadata:
      val getExperiments = new GetExperiments(experimentsOut)
      val getAudits = new GetAudits(experimentsOut, auditsOut)
      val getReplicates = new GetReplicates(experimentsOut, replicatesOut)
      val getFiles = new GetFiles(experimentsOut, filesOut)
      val getTargets = new GetTargets(experimentsOut, targetsOut)
      val getLibraries = new GetLibraries(replicatesOut, librariesOut)
      val getLabs = new GetLabs(librariesOut, labsOut)
      val getSamples = new GetBiosamples(librariesOut, biosamplesOut)
      val getDonors = new GetDonors(biosamplesOut, donorsOut)

      // Transform & combine metadata:
      val extendBamMetadata = new ExtendBamMetadata(filesOut, extendedFilesOut)
      val mergeFileMetadata = new MergeFilesMetadata(
        files = extendedFilesOut,
        replicates = replicatesOut,
        experiments = experimentsOut,
        targets = targetsOut,
        libraries = librariesOut,
        labs = labsOut,
        samples = biosamplesOut,
        donors = donorsOut,
        out = mergedFilesJson
      )
      val mergeDonorMetadata = new MergeDonorsMetadata(
        donors = donorsOut,
        mergedFiles = mergedFilesJson,
        out = mergedDonorsJson
      )
      val cleanFileMetadata = new CleanupFilesMetadata(mergedFilesJson, cleanedFiles)
      val addAudits = new AddAuditMetadata(cleanedFiles, auditsOut, mergedWithAudits)
      val deriveUris = new DeriveActualUris(mergedWithAudits, filesWithUris)
      val buildTransferManifest = new BuildStsManifest(filesWithUris, transferManifest)

      import IngestStep.parallelize

      val run: F[Unit] = for {
        _ <- getExperiments.build
        _ <- parallelize(getAudits, getReplicates, getFiles, getTargets)
        _ <- getLibraries.build
        _ <- parallelize(getLabs, getSamples)
        _ <- parallelize(getDonors, extendBamMetadata)
        _ <- mergeFileMetadata.build
        _ <- parallelize(cleanFileMetadata, mergeDonorMetadata)
        _ <- addAudits.build
        _ <- deriveUris.build
        _ <- buildTransferManifest.build
      } yield {
        ()
      }

      Stream.eval(run)
    }
  }
}
