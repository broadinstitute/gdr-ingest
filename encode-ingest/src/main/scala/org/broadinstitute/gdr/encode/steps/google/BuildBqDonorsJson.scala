package org.broadinstitute.gdr.encode.steps.google

import better.files.File
import cats.effect.Effect
import fs2.Stream
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class BuildBqDonorsJson(donorsMetadata: File, override protected val out: File)
    extends IngestStep {
  import org.broadinstitute.gdr.encode.EncodeFields._

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
    IngestStep
      .readJsonArray(donorsMetadata)
      .map { d =>
        d(DonorIdField).fold(d)(d.add("donor_accession", _).remove(DonorIdField))
      }
      .map(_.asJson.noSpaces)
      .to(IngestStep.writeLines(out))
}
