package org.broadinstitute.gdr.encode.clp

import better.files.File
import cats.data.{Validated, ValidatedNel}
import cats.effect.IO
import cats.implicits._
import com.monovore.decline.{Argument, CommandApp, Opts}
import org.broadinstitute.gdr.encode.steps.{
  BuildUrlManifest,
  SearchExperimentIds,
  SearchFileMetadata
}

object Encode
    extends CommandApp(
      name = "encode-ingest",
      header = "Mirrors data from ENCODE into a Broad repository",
      main = {
        implicit val fileArg: Argument[File] = new Argument[File] {
          override def defaultMetavar: String = "path"
          override def read(string: String): ValidatedNel[String, File] =
            Validated.catchNonFatal(File(string)).leftMap(_.getMessage).toValidatedNel
        }

        val queryExperimentsCommand = Opts.subcommand(
          name = "pull-experiment-ids",
          help = "Query ENCODE for IDs of experiments which should be ingested"
        ) {
          val outOpt = Opts.option[File](
            "output-path",
            help = "Path to which the downloaded experiment ID list should be written"
          )

          outOpt.map(out => new SearchExperimentIds(out))
        }

        val queryFilesCommand = Opts.subcommand(
          name = "pull-file-metadata",
          help =
            "Query ENCODE for metadata of files associated with a set of experiment IDs"
        ) {
          val inOpt = Opts.option[File](
            "experiment-id-list",
            help = "Path to file containing experiment IDs, one per line"
          )

          val outOpt = Opts.option[File](
            "output-path",
            help = "Path to which the downloaded file metadata JSON should be written"
          )

          (inOpt, outOpt).mapN { case (in, out) => new SearchFileMetadata(in, out) }
        }

        val buildFileManifestCommand = Opts.subcommand(
          name = "build-transfer-manifest",
          help =
            "Build a URL manifest which can be fed into Google's STS for ENCODE files which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "file-metadata",
            help =
              "Path to file containing JSON metadata for files downloaded from ENCODE"
          )
          val outOpt = Opts.option[File](
            "output-tsv",
            help = "Path to write generated transfer manifest for ENCODE files"
          )
          val parallelismOpt = Opts.option[Int](
            "parallelism",
            help = "Number of URI derivations to run in parallel"
          )

          (inOpt, outOpt, parallelismOpt).mapN {
            case (in, out, parallelism) => new BuildUrlManifest(in, out, parallelism)
          }
        }

        List(queryExperimentsCommand, queryFilesCommand, buildFileManifestCommand)
          .reduce(_ orElse _)
          .map(_.run[IO].unsafeRunSync())
      }
    )
