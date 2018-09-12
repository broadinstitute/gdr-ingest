package org.broadinstitute.gdr.encode.clp

import java.util.concurrent.Executors

import better.files.File
import cats.data.{Validated, ValidatedNel}
import cats.effect.IO
import cats.implicits._
import com.monovore.decline.{Argument, CommandApp, Opts}
import org.broadinstitute.gdr.encode.steps.download._
import org.broadinstitute.gdr.encode.steps.transfer.BuildUrlManifest
import org.broadinstitute.gdr.encode.steps.transform.{
  CollapseFileMetadata,
  DeriveActualUris
}

import scala.concurrent.ExecutionContext

object Encode
    extends CommandApp(
      name = "encode-ingest",
      header = "Mirrors data from ENCODE into a Broad repository",
      main = {
        val ex = Executors.newCachedThreadPool()
        implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(ex)

        implicit val fileArg: Argument[File] = new Argument[File] {
          override def defaultMetavar: String = "path"
          override def read(string: String): ValidatedNel[String, File] =
            Validated.catchNonFatal(File(string)).leftMap(_.getMessage).toValidatedNel
        }

        val queryExperimentsCommand = Opts.subcommand(
          name = "pull-experiment-metadata",
          help = "Query ENCODE for metadata of experiments which should be ingested"
        ) {
          val outOpt = Opts.option[File](
            "output-path",
            help =
              "Path to which the downloaded experiment metadata JSON should be written"
          )

          outOpt.map(out => new GetExperiments(out))
        }

        val queryAuditsCommand = Opts.subcommand(
          name = "pull-experiment-audits",
          help =
            "Query ENCODE for audit information about experiments which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "experiment-metadata",
            help = "Path to downloaded experiment JSON"
          )

          val outOpt = Opts.option[File](
            "output-path",
            help = "Path to which the downloaded audit metadata JSON should be written"
          )

          (inOpt, outOpt).mapN { case (in, out) => new GetAudits(in, out) }
        }

        val queryFilesCommand = Opts.subcommand(
          name = "pull-file-metadata",
          help = "Query ENCODE for metadata of files which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "experiment-metadata",
            help = "Path to downloaded experiment JSON"
          )

          val outOpt = Opts.option[File](
            "output-path",
            help = "Path to which the downloaded file metadata JSON should be written"
          )

          (inOpt, outOpt).mapN { case (in, out) => new GetFiles(in, out) }
        }

        val queryReplicatesCommand = Opts.subcommand(
          name = "pull-replicate-metadata",
          help = "Query ENCODE for metadata of replicates which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "experiment-metadata",
            help = "Path to downloaded experiment JSON"
          )

          val outOpt = Opts.option[File](
            "output-path",
            help =
              "Path to which the downloaded replicate metadata JSON should be written"
          )

          (inOpt, outOpt).mapN { case (in, out) => new GetReplicates(in, out) }
        }

        val queryTargetsCommand = Opts.subcommand(
          name = "pull-target-metadata",
          help = "Query ENCODE for metadata of targets which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "experiment-metadata",
            help = "Path to downloaded experiment JSON"
          )

          val outOpt = Opts.option[File](
            "output-path",
            help = "Path to which the downloaded target metadata JSON should be written"
          )

          (inOpt, outOpt).mapN { case (in, out) => new GetTargets(in, out) }
        }

        val queryLibrariesCommand = Opts.subcommand(
          name = "pull-library-metadata",
          help = "Query ENCODE for metadata of libraries which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "replicate-metadata",
            help = "Path to downloaded replicate JSON"
          )

          val outOpt = Opts.option[File](
            "output-path",
            help = "Path to which the downloaded library metadata JSON should be written"
          )

          (inOpt, outOpt).mapN { case (in, out) => new GetLibraries(in, out) }
        }

        val queryLabsCommand = Opts.subcommand(
          name = "pull-lab-metadata",
          help = "Query ENCODE for metadata of labs which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "library-metadata",
            help = "Path to downloaded library JSON"
          )

          val outOpt = Opts.option[File](
            "output-path",
            help = "Path to which the downloaded lab metadata JSON should be written"
          )

          (inOpt, outOpt).mapN { case (in, out) => new GetLabs(in, out) }
        }

        val queryBiosamplesCommand = Opts.subcommand(
          name = "pull-biosample-metadata",
          help = "Query ENCODE for metadata of biosamples which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "library-metadata",
            help = "Path to downloaded library JSON"
          )

          val outOpt = Opts.option[File](
            "output-path",
            help =
              "Path to which the downloaded biosample metadata JSON should be written"
          )

          (inOpt, outOpt).mapN { case (in, out) => new GetBiosamples(in, out) }
        }

        val queryDonorsCommand = Opts.subcommand(
          name = "pull-donor-metadata",
          help = "Query ENCODE for metadata of donors which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "biosample-metadata",
            help = "Path to downloaded biosample JSON"
          )

          val outOpt = Opts.option[File](
            "output-path",
            help = "Path to which the downloaded donor metadata JSON should be written"
          )

          (inOpt, outOpt).mapN { case (in, out) => new GetDonors(in, out) }
        }

        val collapseFileGraphCommand = Opts.subcommand(
          name = "collapse-file-metadata",
          help =
            "Collapse downloaded file metadata to include only records which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "file-metadata",
            help = "Path to downloaded file JSON which should be collapsed"
          )
          val outOpt = Opts.option[File](
            "output-path",
            help = "Path to which collapsed metadata should be written"
          )

          (inOpt, outOpt).mapN { case (in, out) => new CollapseFileMetadata(in, out) }
        }

        val deriveActualUrisCommand = Opts.subcommand(
          name = "derive-download-uris",
          help =
            "Derive and append actual download URIs to the metadata for a set of ENCODE files"
        ) {
          val inOpt = Opts.option[File](
            "file-metadata",
            help =
              "Path to downloaded file JSON for which download URIs should be derived"
          )
          val outOpt = Opts.option[File](
            "output-path",
            help = "Path to which augmented file metadata should be written"
          )

          (inOpt, outOpt).mapN { case (in, out) => new DeriveActualUris(in, out) }
        }

        val buildFileManifestCommand = Opts.subcommand(
          name = "build-transfer-manifest",
          help =
            "Build a URL manifest which can be fed into Google's STS for ENCODE files which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "file-metadata",
            help = "Path to file JSON containing derived download URIs"
          )
          val outOpt = Opts.option[File](
            "output-tsv",
            help = "Path to write generated transfer manifest for ENCODE files"
          )

          (inOpt, outOpt).mapN { case (in, out) => new BuildUrlManifest(in, out) }
        }

        List(
          queryExperimentsCommand,
          queryAuditsCommand,
          queryFilesCommand,
          queryReplicatesCommand,
          queryLabsCommand,
          queryTargetsCommand,
          queryLibrariesCommand,
          queryBiosamplesCommand,
          queryDonorsCommand,
          collapseFileGraphCommand,
          deriveActualUrisCommand,
          buildFileManifestCommand
        ).reduce(_ orElse _).map { cmd =>
          val res = cmd.build[IO].attempt.unsafeRunSync()
          val _ = ex.shutdownNow()
          res.valueOr(throw _)
        }
      }
    )
