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
  import org.broadinstitute.gdr.encode.EncodeFields._
  import AddAuditMetadata._

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
    Stream
      .eval(auditMessagesToInfo[F])
      .flatMap { auditInfo =>
        IngestStep.readJsonArray(mergedFilesJson).map(linkAudits(auditInfo))
      }
      .to(IngestStep.writeJsonArray(out))

  private def auditMessagesToInfo[F[_]: Sync]: F[Map[String, (Int, Set[String])]] =
    IngestStep
      .readJsonArray(auditJson)
      .map { js =>
        for {
          level <- js("level").flatMap(_.as[Int].toOption)
          category <- js("category").flatMap(_.asString)
          path <- js("path").flatMap(_.asString)
          detail <- js("detail").flatMap(_.asString)
        } yield {
          RefPattern
            .findAllMatchIn(detail)
            .map(m => (m.group(1), (level, s"${AuditColors(level)}: $category ($path)")))
            .toMap
        }
      }
      .unNone
      .compile
      .fold(Map.empty[String, (Int, Set[String])]) { (acc, levels) =>
        levels.foldLeft(acc) {
          case (inner, (ref, (level, summary))) =>
            val (prevLevel, prevSummaries) = inner.getOrElse(ref, (0, Set.empty[String]))
            val newValue = (math.max(level, prevLevel), prevSummaries + summary)
            inner + (ref -> newValue)
        }
      }

  private def linkAudits(refToMaxLevel: Map[String, (Int, Set[String])])(
    mergedJson: JsonObject
  ): JsonObject = {
    val markers = auditMarkers(mergedJson)
    val (maxAuditLevel, summaries) =
      markers.map(refToMaxLevel.getOrElse(_, (0, Set.empty[String]))).maxBy(_._1)
    mergedJson
      .add(AuditColorField, AuditColors(maxAuditLevel).asJson)
      .add(AuditWarningsField, summaries.asJson)
  }

  private def auditMarkers(mergedJson: JsonObject): Iterable[String] = {
    val fileId = mergedJson(FileIdField).flatMap(_.asString)
    val expDerived = mergedJson(DerivedFromExperimentField)
      .flatMap(_.asArray.map(_.flatMap(_.asString)))
    val refDerived = mergedJson(DerivedFromReferenceField)
      .flatMap(_.asArray.map(_.flatMap(_.asString)))
    val experimentIds = mergedJson(ExperimentLinkField)
      .flatMap(_.asArray.map(_.flatMap(_.asString)))
    val replicateIds = mergedJson(ReplicateLinkField)
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
  )
}
