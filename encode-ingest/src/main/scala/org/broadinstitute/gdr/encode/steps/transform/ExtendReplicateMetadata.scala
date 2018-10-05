package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.Effect
import fs2.Stream
import io.circe.JsonObject
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class ExtendReplicateMetadata(
  replicateMetadata: File,
  experimentMetadata: File,
  targetMetadata: File,
  libraryMetadata: File,
  labMetadata: File,
  sampleMetadata: File,
  donorMetadata: File,
  override protected val out: File
) extends IngestStep {
  import org.broadinstitute.gdr.encode.EncodeFields._

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
    Stream(
      experimentMetadata,
      targetMetadata,
      libraryMetadata,
      labMetadata,
      sampleMetadata,
      donorMetadata
    ).evalMap(IngestStep.readLookupTable[F])
      .fold(Map.empty[String, JsonObject])(_ ++ _)
      .map(extendMetadata)
      .flatMap { join =>
        val transforms = Seq(
          join(ExperimentPrefix, ExperimentPrefix, ExperimentFields),
          join(LibraryPrefix, LibraryPrefix, LibraryFields),
          join(joinedName(TargetPrefix, ExperimentPrefix), TargetPrefix, TargetFields),
          join(
            joinedName(BiosamplePrefix, LibraryPrefix),
            BiosamplePrefix,
            BiosampleFields
          ),
          join(joinedName(LabPrefix, LibraryPrefix), LabPrefix, LabFields),
          join(joinedName(DonorPrefix, BiosamplePrefix), DonorPrefix, Set(DonorIdField))
        )

        val replicates = IngestStep
          .readJsonArray(replicateMetadata)
          .map(_.filterKeys((ReplicateFields + EncodeIdField).contains))

        transforms.foldLeft(replicates)(_.map(_).unNone)
      }
      .to(IngestStep.writeJsonArray(out))

  /**
    * Extend JSON metadata by replacing a foreign-key field with a set of fields
    * associated with the entity pointed to by the key.
    */
  private def extendMetadata(idToMetadata: Map[String, JsonObject])(
    foreignKeyField: String,
    joinedFieldPrefix: String,
    fieldsToJoin: Set[String]
  )(metadata: JsonObject): Option[JsonObject] = {
    val foreignMetadata = for {
      foreignKey <- metadata(foreignKeyField).flatMap(_.asString)
      foreignMetadata <- idToMetadata.get(foreignKey)
    } yield {
      fieldsToJoin.flatMap { field =>
        foreignMetadata(field).map(joinedName(field, joinedFieldPrefix) -> _)
      }.toMap
    }

    foreignMetadata.map { fm =>
      JsonObject.fromMap(metadata.toMap ++ fm).remove(foreignKeyField)
    }
  }
}
