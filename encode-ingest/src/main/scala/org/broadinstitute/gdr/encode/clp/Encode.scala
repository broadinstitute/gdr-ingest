package org.broadinstitute.gdr.encode.clp

import java.util.concurrent.Executors

import better.files.File
import cats.data.{Validated, ValidatedNel}
import cats.effect.IO
import cats.implicits._
import com.monovore.decline.{Argument, CommandApp, Opts}
import org.broadinstitute.gdr.encode.steps.{BuildUrlManifest, GetFiles, SearchExperiments}

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

          outOpt.map(out => new SearchExperiments(out))
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

        val buildFileManifestCommand = Opts.subcommand(
          name = "build-transfer-manifest",
          help =
            "Build a URL manifest which can be fed into Google's STS for ENCODE files which should be ingested"
        ) {
          val inOpt = Opts.option[File](
            "file-metadata",
            help = "Path to downloaded file JSON"
          )
          val outOpt = Opts.option[File](
            "output-tsv",
            help = "Path to write generated transfer manifest for ENCODE files"
          )

          (inOpt, outOpt).mapN { case (in, out) => new BuildUrlManifest(in, out) }
        }

        List(queryExperimentsCommand, queryFilesCommand, buildFileManifestCommand)
          .reduce(_ orElse _)
          .map { cmd =>
            cmd.run[IO].unsafeRunSync()
            val _ = ex.shutdownNow()
          }
      }
    )
