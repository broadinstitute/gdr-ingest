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

    val fileRows =
      IngestStep
        .readJsonArray(filesJson)
        .filter(oneParticipant)
        .map(buildRow(CreateSamplesTsv.ObjectFields))

    Stream
      .emit(CreateSamplesTsv.TsvHeaders)
      .append(fileRows)
      .map(_.mkString("\t"))
      .to(IngestStep.writeLines(out))
  }

  private def oneParticipant(fileJson: JsonObject): Boolean =
    fileJson(CleanupFilesMetadata.DonorFkField)
      .flatMap(_.asArray)
      .fold(false)(_.length == 1)

  private def buildRow(fields: List[String])(fileJson: JsonObject): List[String] =
    fields.foldRight(List.empty[String]) { (field, acc) =>
      val rawValue = fileJson(field).getOrElse(Json.fromString(""))
      val tsvValue = field match {
        case DeriveActualUris.DownloadUriField =>
          rawValue.mapString(
            _.replaceFirst("^https://", s"gs://${CreateSamplesTsv.RawStorageBucket}/")
          )
        case CleanupFilesMetadata.DonorFkField => rawValue.withArray(_.head)
        case _                                 => rawValue
      }

      tsvValue.noSpaces :: acc
    }
}

object CreateSamplesTsv {

  val RawStorageBucket = "broad-gdr-encode-storage"

  val TsvHeaders = List.concat(
    List(
      "entity:sample_id",
      "participant_id",
      DeriveActualUris.DownloadUriField
    ),
    CleanupFilesMetadata.FinalFields -- Set(
      CleanupFilesMetadata.FileAccessionField,
      CleanupFilesMetadata.DonorFkField,
      "href"
    )
  )

  val ObjectFields = List.concat(
    List(CleanupFilesMetadata.FileAccessionField, CleanupFilesMetadata.DonorFkField),
    TsvHeaders.drop(2)
  )
}
