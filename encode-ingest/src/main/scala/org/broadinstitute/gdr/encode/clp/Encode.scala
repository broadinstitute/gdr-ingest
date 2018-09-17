package org.broadinstitute.gdr.encode.clp

import java.util.concurrent.Executors

import better.files.File
import cats.data.{Validated, ValidatedNel}
import cats.effect.IO
import cats.implicits._
import com.monovore.decline.{Argument, CommandApp, Opts}
import org.broadinstitute.gdr.encode.steps.PrepareIngest

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

        prepIngest.map { cmd =>
          val res = cmd.build[IO].attempt.unsafeRunSync()
          val _ = ex.shutdownNow()
          res.valueOr(throw _)
        }
      }
    )
