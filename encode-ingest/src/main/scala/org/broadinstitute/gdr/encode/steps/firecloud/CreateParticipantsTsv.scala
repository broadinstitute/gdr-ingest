package org.broadinstitute.gdr.encode.steps.firecloud

import better.files.File
import cats.effect.Effect
import fs2.Stream
import io.circe.JsonObject
import org.broadinstitute.gdr.encode.steps.IngestStep
import org.broadinstitute.gdr.encode.steps.transform.MergeDonorsMetadata

import scala.language.higherKinds

class CreateParticipantsTsv(donorsJson: File, override protected val out: File)
    extends IngestStep {

  override protected def process[F[_]: Effect]: Stream[F, Unit] = {
    val tsvHeaders = CreateParticipantsTsv.IdHeader ::
      (MergeDonorsMetadata.DonorFields - MergeDonorsMetadata.IdField).toList
    val jsonFields = MergeDonorsMetadata.IdField :: tsvHeaders.tail

    val donorRows = IngestStep.readJsonArray(donorsJson).map(buildRow(jsonFields))

    Stream
      .emit(tsvHeaders)
      .append(donorRows)
      .map(_.mkString("\t"))
      .to(IngestStep.writeLines(out))
  }

  private def buildRow(fields: List[String])(donorJson: JsonObject): List[String] =
    fields.foldRight(List.empty[String]) { (field, acc) =>
      donorJson(field).map(_.noSpaces).getOrElse("") :: acc
    }
}

object CreateParticipantsTsv {
  val IdHeader = "entity:participant_id"
}
