package org.broadinstitute.gdr.encode.clp

import java.util.concurrent.Executors

import better.files.File
import cats.data.{Validated, ValidatedNel}
import cats.effect.IO
import cats.implicits._
import com.monovore.decline.{Argument, CommandApp, Opts}
import fs2.Scheduler
import org.broadinstitute.gdr.encode.steps.PrepareIngest
import org.broadinstitute.gdr.encode.steps.firecloud.CreateTsvs
import org.broadinstitute.gdr.encode.steps.google.BuildBqJsons

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

        val prepIngest = Opts.subcommand(
          name = "prep-ingest",
          help =
            "Generate files which can be manually fed into BigQuery / STS to ingest ChIP-Seq data from ENCODE"
        ) {
          Opts
            .option[File](
              "output-dir",
              help = "Directory into which generated files should be written"
            )
            .map(new PrepareIngest(_))
        }

        val genFirecloud = Opts.subcommand(
          name = "generate-firecloud-tsvs",
          help =
            "Generate TSVs from prepared ENCODE metadata, for upload to a FireCloud workspace"
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
            help = "Directory into which generated TSVs should be written"
          )

          (filesOpt, donorsOpt, bucketOpt, outOpt).mapN {
            case (files, donors, bucket, out) =>
              new CreateTsvs(files, donors, bucket, out)
          }
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
            "output-dir",
            help = "Directory into which generated JSONs should be written"
          )

          (filesOpt, donorsOpt, bucketOpt, outOpt).mapN {
            case (files, donors, bucket, out) =>
              new BuildBqJsons(files, donors, bucket, out)
          }
        }

        prepIngest.orElse(genFirecloud).orElse(genBq).map { cmd =>
          val res = cmd.build[IO].attempt.unsafeRunSync()
          val _ = (ioExecutor.shutdownNow(), schedulerExecutor.shutdownNow())
          res.valueOr(throw _)
        }
      }
    )
