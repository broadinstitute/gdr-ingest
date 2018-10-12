package org.broadinstitute.gdr.encode.steps.cromwell

import better.files.File
import cats.effect.Effect
import fs2.Stream
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep
import org.broadinstitute.gdr.encode.steps.transform.{
  DeriveActualUris,
  JoinReplicatesToFiles,
  ShapeFileMetadata
}

import scala.language.higherKinds

class BuildCromwellInputs(fileMetadata: File, override protected val out: File)
    extends IngestStep {

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
    if (!out.isDirectory) {
      Stream.raiseError(
        new IllegalArgumentException(
          s"Output must be pointed at a directory, $out is not a directory"
        )
      )
    } else {
      IngestStep
        .readJsonArray(fileMetadata)
        .filter { file =>
          val isBam = for {
            isAvailable <- file(JoinReplicatesToFiles.FileAvailableField)
              .flatMap(_.asBoolean)
            format <- file("file_format").flatMap(_.asString)
          } yield {
            isAvailable && format == "bam"
          }

          isBam.getOrElse(false)
        }
        .segmentN(200)
        .map { bamBatch =>
          val (_, inputBatch) = bamBatch
            .fold(Vector.empty[JsonObject]) { (acc, bam) =>
              wdlInput(bam).fold(acc)(_ +: acc)
            }
            .force
            .run

          JsonObject.singleton(
            s"${BuildCromwellInputs.WorkflowName}.bams",
            inputBatch.asJson
          )
        }
        .zipWithIndex
        .flatMap {
          case (inputJson, i) =>
            val byteStream = Stream.emits(inputJson.asJson.noSpaces.getBytes).covary[F]
            val inputsFile = out / s"${BuildCromwellInputs.WorkflowName}.inputs.$i.json"

            byteStream.to(fs2.io.file.writeAll(inputsFile.path))
        }
    }

  private def wdlInput(bamJson: JsonObject): Option[JsonObject] =
    for {
      accession <- bamJson(ShapeFileMetadata.FileAccessionField)
      href <- bamJson(DeriveActualUris.DownloadUriField)
      size <- bamJson("file_size")
      md5 <- bamJson("md5sum")
    } yield {
      JsonObject(
        "bam_accession" -> accession,
        "bam_href" -> href,
        "bam_size" -> size,
        "bam_md5" -> md5
      )
    }
}

object BuildCromwellInputs {
  val WorkflowName = "PreprocessEncodeBams"
}
