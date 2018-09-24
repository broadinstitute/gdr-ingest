package org.broadinstitute.gdr.encode.steps.firecloud

import better.files.File
import cats.effect.Effect
import fs2.Stream
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class CreateTsvs(filesJson: File, donorsJson: File, protected override val out: File)(
  implicit ec: ExecutionContext
) extends IngestStep {

  override protected def process[F[_]: Effect]: Stream[F, Unit] = {
    if (!out.isDirectory) {
      Stream.raiseError(
        new IllegalArgumentException(
          s"Generation must be pointed at a directory, $out is not a directory"
        )
      )
    } else {
      val samples = new CreateSamplesTsv(filesJson, out / "samples.tsv")
      val participants = new CreateParticipantsTsv(donorsJson, out / "participants.tsv")

      Stream.eval(IngestStep.parallelize(samples, participants))
    }
  }

}
