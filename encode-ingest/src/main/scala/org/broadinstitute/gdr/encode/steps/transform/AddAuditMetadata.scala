package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.Stream
import io.circe.JsonObject
import io.circe.syntax._
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds
import scala.util.matching.Regex

class AddAuditMetadata(
  mergedFilesJson: File,
  auditJson: File,
  override protected val out: File
) extends IngestStep {
  import AddAuditMetadata._

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
    Stream
      .eval(auditMessagesToLevels[F])
      .flatMap { auditInfo =>
        IngestStep.readJsonArray(mergedFilesJson).map(linkAudits(auditInfo))
      }
      .to(IngestStep.writeJsonArray(out))

  private def auditMessagesToLevels[F[_]: Sync]: F[Map[String, Int]] =
    IngestStep
      .readJsonArray(auditJson)
      .map { js =>
        for {
          level <- js("level").flatMap(_.as[Int].toOption)
          detail <- js("detail").flatMap(_.asString)
        } yield {
          RefPattern.findAllMatchIn(detail).map(_.group(1) -> level).toMap
        }
      }
      .unNone
      .compile
      .fold(Map.empty[String, Int]) { (acc, levels) =>
        levels.foldLeft(acc) {
          case (inner, (ref, level)) =>
            inner + (ref -> math.max(level, inner.getOrElse(ref, 0)))
        }
      }

  private def linkAudits(refToMaxLevel: Map[String, Int])(
    mergedJson: JsonObject
  ): JsonObject = {
    val markers = auditMarkers(mergedJson)
    val maxAuditLevel = markers.map(refToMaxLevel.getOrElse(_, 0)).max
    mergedJson.add(AuditColorField, AuditColors(maxAuditLevel))
  }

  private def auditMarkers(mergedJson: JsonObject): Iterable[String] = {
    import MergeMetadata._

    val fileId = mergedJson("accession").flatMap(_.asString)
    val expDerived = mergedJson(ExtendBamMetadata.DerivedFromExperimentField)
      .flatMap(_.asArray.map(_.flatMap(_.asString)))
    val refDerived = mergedJson(ExtendBamMetadata.DerivedFromReferenceField)
      .flatMap(_.asArray.map(_.flatMap(_.asString)))
    val experimentIds = mergedJson(joinedName("accession", ExperimentPrefix))
      .flatMap(_.asArray.map(_.flatMap(_.asString)))
    val replicateIds = mergedJson(joinedName("uuid", ReplicatePrefix))
      .flatMap(_.asArray.map(_.flatMap(_.asString)))

    Iterable.concat(
      fileId,
      expDerived.toIterable.flatten,
      refDerived.toIterable.flatten,
      experimentIds.toIterable.flatten,
      replicateIds.toIterable.flatten
    )
  }
}

object AddAuditMetadata {

  val RefPattern: Regex = "/[^/]+/([^/]+)/".r

  val AuditColors = Map(
    0 -> "white",
    30 -> "white",
    40 -> "yellow",
    50 -> "orange",
    60 -> "red"
  ).mapValues(_.asJson)

  val AuditColorField = "audit_color"
}
