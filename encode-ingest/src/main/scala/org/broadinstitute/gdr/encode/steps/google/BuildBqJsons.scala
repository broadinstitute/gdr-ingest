package org.broadinstitute.gdr.encode.steps.google

import better.files.File
import cats.effect.Effect
import fs2.Stream
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class BuildBqJsons(
  fileMetadata: File,
  donorsMetadata: File,
  storageBucket: String,
  override protected val out: File
)(implicit ec: ExecutionContext)
    extends IngestStep {

  override protected def process[F[_]: Effect]: Stream[F, Unit] = {
    if (!out.isDirectory) {
      Stream.raiseError(
        new IllegalArgumentException(
          s"Generation must be pointed at a directory, $out is not a directory"
        )
      )
    } else {
      val files = new BuildBqFilesJson(fileMetadata, storageBucket, out / "files.bq.json")
      val donors = new BuildBqDonorsJson(donorsMetadata, out / "donors.bq.json")

      Stream.eval(IngestStep.parallelize(files, donors))
    }
  }
}
