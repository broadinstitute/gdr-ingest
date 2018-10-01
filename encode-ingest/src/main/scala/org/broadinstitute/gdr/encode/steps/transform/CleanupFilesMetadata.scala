package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class CleanupFilesMetadata(mergedFiles: File, override protected val out: File)
    extends IngestStep {
  import org.broadinstitute.gdr.encode.EncodeFields._

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
    IngestStep
      .readJsonArray(mergedFiles)
      .map(stripControls)
      .evalMap(flattenSingletons[F])
      .map(renameFields)
      .map(_.filterKeys(FinalFileFields.contains))
      .to(IngestStep.writeJsonArray(out))

  private def stripControls(mergedFile: JsonObject): JsonObject = {
    val strippedTargets = for {
      labelJson <- mergedFile(SuffixedLabel)
      labels <- labelJson.as[Seq[String]].toOption
    } yield {
      if (labels.length <= 1) {
        labels
      } else {
        labels.filterNot(_.matches(".*[Cc]ontrol.*"))
      }
    }
    strippedTargets.fold(mergedFile)(ts => mergedFile.add(SuffixedLabel, ts.asJson))
  }

  private def flattenSingletons[F[_]: Sync](mergedFile: JsonObject): F[JsonObject] =
    FieldsToFlatten.foldLeft(Sync[F].pure(mergedFile)) { (wrappedAcc, field) =>
      val listField = s"$field$JoinedSuffix"
      val maybeFlattened = for {
        fieldJson <- mergedFile(listField)
        fieldArray <- fieldJson.asArray
        if fieldArray.length == 1
      } yield {
        field -> fieldArray.head
      }

      for {
        acc <- wrappedAcc
        flattened <- Sync[F].fromOption(
          maybeFlattened,
          new IllegalStateException(
            s"'$listField' is not a singleton array in $mergedFile"
          )
        )
      } yield {
        (flattened +: acc).remove(listField)
      }
    }

  private def renameFields(mergedFile: JsonObject): JsonObject =
    FieldsToRename.foldLeft(mergedFile) {
      case (acc, (oldName, newName)) =>
        acc(oldName).fold(acc)(v => acc.add(newName, v).remove(oldName))
    }
}
