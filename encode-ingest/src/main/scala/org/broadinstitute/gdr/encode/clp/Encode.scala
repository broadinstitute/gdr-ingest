package org.broadinstitute.gdr.encode.clp

import java.util.concurrent.Executors

import better.files.File
import cats.data.{Validated, ValidatedNel}
import cats.effect.IO
import cats.implicits._
import com.monovore.decline.{Argument, CommandApp, Opts}
import org.broadinstitute.gdr.encode.steps.download.GetAllMetadata
import org.broadinstitute.gdr.encode.steps.transfer.BuildUrlManifest
import org.broadinstitute.gdr.encode.steps.transform.{
  CollapseFileMetadata,
  DeriveActualUris,
  MergeFilesMetadata
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

        val downloadMetadataCommand = Opts.subcommand(
          name = "download-metadata",
          help = "Download all metadata tied to released ChIP-Seq experiments from ENCODE"
        ) {
          val outOpt = Opts.option[File](
            "output-dir",
            help = "Directory into which downloaded JSON should be written"
          )

          outOpt.map(new GetAllMetadata(_))
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

        val mergeFilesMetadataCommand = Opts.subcommand(
          name = "merge-files-metadata",
          help =
            "Merge downloaded metadata to build file-level JSON to upload to a repository"
        ) {
          val filesOpt = Opts.option[File](
            "file-metadata",
            help =
              "Path to downloaded file JSON, pre-processed to include replicate information and download URIs"
          )
          val replicatesOpt = Opts.option[File](
            "replicate-metadata",
            help = "Path to downloaded replicate JSON"
          )
          val experimentsOpt = Opts.option[File](
            "experiment-metadata",
            help = "Path to downloaded experiment JSON"
          )
          val targetsOpt = Opts.option[File](
            "target-metadata",
            help = "Path to downloaded target JSON"
          )
          val librariesOpt = Opts.option[File](
            "library-metadata",
            help = "Path to downloaded library JSON"
          )
          val labsOpt = Opts.option[File](
            "lab-metadata",
            help = "Path to downloaded lab JSON"
          )
          val samplesOpt = Opts.option[File](
            "samples-metadata",
            help = "Path to downloaded biosamples JSON"
          )
          val outOpt = Opts.option[File](
            "output-path",
            help = "Path to which combined metadata should be written"
          )

          (
            filesOpt,
            replicatesOpt,
            experimentsOpt,
            targetsOpt,
            librariesOpt,
            labsOpt,
            samplesOpt,
            outOpt
          ).mapN {
            case (
                files,
                replicates,
                experiments,
                targets,
                libraries,
                labs,
                samples,
                out
                ) =>
              new MergeFilesMetadata(
                files,
                replicates,
                experiments,
                targets,
                libraries,
                labs,
                samples,
                out
              )
          }
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
          downloadMetadataCommand,
          collapseFileGraphCommand,
          deriveActualUrisCommand,
          mergeFilesMetadataCommand,
          buildFileManifestCommand
        ).reduce(_ orElse _).map { cmd =>
          val res = cmd.build[IO].attempt.unsafeRunSync()
          val _ = ex.shutdownNow()
          res.valueOr(throw _)
        }
      }
    )
