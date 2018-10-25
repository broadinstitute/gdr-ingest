package org.broadinstitute.gdr.encode.clp

import java.util.concurrent.Executors

import better.files.File
import cats.data.{Validated, ValidatedNel}
import cats.effect.IO
import cats.implicits._
import com.monovore.decline.{Argument, CommandApp, Opts}
import fs2.Scheduler
import org.broadinstitute.gdr.encode.steps.download.DownloadMetadata
import org.broadinstitute.gdr.encode.steps.google.BuildBqJson
import org.broadinstitute.gdr.encode.steps.rawls.BuildRawlsJsons
import org.broadinstitute.gdr.encode.steps.transform.PrepareMetadata

import scala.concurrent.ExecutionContext

/**
  * Lightweight CLI wrapper for demo ENCODE ingest logic.
  */
object Encode
    extends CommandApp(
      name = "encode-ingest",
      header = "Mirrors data from ENCODE into a Broad repository",
      main = {
        val ioExecutor = Executors.newCachedThreadPool()
        val schedulerExecutor = Executors.newScheduledThreadPool(1)

        implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(ioExecutor)
        implicit val s: Scheduler =
          Scheduler.fromScheduledExecutorService(schedulerExecutor)

        implicit val fileArg: Argument[File] = new Argument[File] {
          override def defaultMetavar: String = "path"
          override def read(string: String): ValidatedNel[String, File] =
            Validated.catchNonFatal(File(string)).leftMap(_.getMessage).toValidatedNel
        }

        val downloadMetadata = Opts.subcommand(
          name = "download-metadata",
          help = "Download raw metadata from ENCODE for entities which should be ingested"
        ) {
          Opts
            .option[File](
              "output-dir",
              help = "Directory into which downloaded JSON should be written"
            )
            .map(new DownloadMetadata(_))
        }

        val prepareMetadata = Opts.subcommand(
          name = "prepare-metadata",
          help =
            "Transform raw metadata from ENCODE into files which can be fed to downstream processes"
        ) {
          Opts
            .option[File](
              "download-dir",
              help =
                "Directory containing downloaded metadata. Prepared metadata will be written to the same location"
            )
            .map(new PrepareMetadata(_))
        }

        val genBq = Opts.subcommand(
          name = "generate-bigquery-json",
          help = "Generate JSON for upload to BigQuery from prepared ENCODE metadata"
        ) {
          val filesOpt = Opts.option[File](
            "files-json",
            help = "Final files JSON produced by the 'prep-ingest' step"
          )
          val donorsOpt = Opts.option[File](
            "donors-json",
            help = "Final donors JSON produced by the 'prep-ingest' step"
          )
          val bucketOpt = Opts.option[String](
            "transfer-bucket",
            help = "Bucket containing raw ENCODE data from a run of Google's STS"
          )
          val outOpt = Opts.option[File](
            "output",
            help = "Path to where JSON output should be written"
          )

          (filesOpt, donorsOpt, bucketOpt, outOpt).mapN {
            case (files, donors, bucket, out) =>
              new BuildBqJson(files, donors, bucket, out)
          }
        }

        val genRawls = Opts.subcommand(
          name = "generate-rawls-json",
          help = "Generate JSON for upload to Rawls from prepared ENCODE metadata"
        ) {
          val filesOpt = Opts.option[File](
            "files-json",
            help = "Final files JSON produced by the 'prep-ingest' step"
          )
          val donorsOpt = Opts.option[File](
            "donors-json",
            help = "Final donors JSON produced by the 'prep-ingest' step"
          )
          val bucketOpt = Opts.option[String](
            "transfer-bucket",
            help = "Bucket containing raw ENCODE data from a run of Google's STS"
          )
          val outOpt = Opts.option[File](
            "output-dir",
            help = "Directory into which generated files should be written"
          )

          (filesOpt, donorsOpt, bucketOpt, outOpt).mapN {
            case (files, donors, bucket, out) =>
              new BuildRawlsJsons(files, donors, bucket, out)
          }
        }

        downloadMetadata.orElse(prepareMetadata).orElse(genBq).orElse(genRawls).map {
          cmd =>
            val res = cmd.build[IO].attempt.unsafeRunSync()
            val _ = (ioExecutor.shutdownNow(), schedulerExecutor.shutdownNow())
            res.valueOr(throw _)
        }
      }
    )
