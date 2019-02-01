package org.broadinstitute.gdr.encode.ingest.steps.load

import better.files.File
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.syntax.all._
import fs2.Stream
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class ExportMetadata(
  storageBucket: String,
  override protected val out: File,
  blockingContext: ExecutionContext
) extends IngestStep {
  override protected def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] = {
    if (!out.isDirectory) {
      Stream.raiseError(
        new IllegalArgumentException(s"$out is not a directory")
      )
    } else {
      val filesWithUris = out / "files.joined.json"

      val cleanedFiles = out / "files.cleaned.json"
      val cleanedDonors = out / "donors.cleaned.json"

      val sqlOut = out / "snapshot.sql"

      val prepFiles = new SwapFileFields(
        filesWithUris,
        storageBucket,
        cleanedFiles,
        blockingContext
      )

      val buildSql = new BuildSqlSnapshot(
        cleanedFiles,
        cleanedDonors,
        sqlOut,
        blockingContext
      )

      val run: F[Unit] = for {
        _ <- prepFiles.build
        _ <- buildSql.build
      } yield {
        ()
      }

      Stream.eval(run)
    }
  }
}
