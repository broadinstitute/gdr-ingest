package org.broadinstitute.gdr.encode.steps.firecloud

import better.files.File
import cats.effect.Effect
import fs2.Stream
import io.circe.{Json, JsonObject}
import org.broadinstitute.gdr.encode.steps.IngestStep
import org.broadinstitute.gdr.encode.steps.transform.{
  CleanupFilesMetadata,
  DeriveActualUris
}

import scala.language.higherKinds

class CreateSamplesTsv(filesJson: File, override protected val out: File)
    extends IngestStep {
  override protected def process[F[_]: Effect]: Stream[F, Unit] = {
    val tsvHeaders = CreateSamplesTsv.IdHeader :: CreateSamplesTsv.ParticipantHeader ::
      (CleanupFilesMetadata.FinalFields - CleanupFilesMetadata.FileAccessionField - CleanupFilesMetadata.DonorFkField).toList
    val jsonFields = CleanupFilesMetadata.FileAccessionField :: CleanupFilesMetadata.DonorFkField :: tsvHeaders
      .drop(2)

    val fileRows =
      IngestStep
        .readJsonArray(filesJson) /*.filter(oneParticipant)*/
        .map(buildRow(jsonFields))

    Stream
      .emit(tsvHeaders)
      .append(fileRows)
      .map(_.mkString("\t"))
      .to(IngestStep.writeLines(out))
  }

  /*private def oneParticipant(fileJson: JsonObject): Boolean =
    fileJson(CleanupFilesMetadata.DonorFkField)
      .flatMap(_.asArray)
      .fold(false)(_.length == 1)*/

  private def buildRow(fields: List[String])(fileJson: JsonObject): List[String] =
    fields.foldRight(List.empty[String]) { (field, acc) =>
      val rawValue = fileJson(field).getOrElse(Json.fromString(""))
      val tsvValue = field match {
        case DeriveActualUris.DownloadUriField =>
          rawValue.mapString(_.replaceFirst("^https://", "gs://"))
        //case CleanupFilesMetadata.DonorFkField => rawValue.withArray(_.head)
        case _ => rawValue
      }

      tsvValue.noSpaces :: acc
    }
}

object CreateSamplesTsv {
  val IdHeader = "entity:sample_id"
  val ParticipantHeader = "participant_id"
}
