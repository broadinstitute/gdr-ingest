package org.broadinstitute.gdr.encode.steps.transform

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import cats.kernel.Monoid
import fs2.Stream
import io.circe.{Decoder, JsonObject}
import io.circe.syntax._
import org.broadinstitute.gdr.encode.EncodeFields
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.language.higherKinds

class JoinAuditsToFiles(
  auditMetadata: File,
  fileMetadata: File,
  override protected val out: File
) extends IngestStep {
  import JoinAuditsToFiles._

  override protected def process[F[_]: Effect]: Stream[F, Unit] =
    Stream
      .eval(idsToAuditInfo[F])
      .flatMap { infoTable =>
        IngestStep.readJsonArray(fileMetadata).map(joinAuditInfo(infoTable))
      }
      .to(IngestStep.writeJsonArray(out))

  private def idsToAuditInfo[F[_]: Sync]: F[Map[String, AuditInfo]] =
    IngestStep.readLookupTable(auditMetadata).map { idsToAudits =>
      idsToAudits.mapValues { js =>
        js(EncodeFields.EncodeAuditField).flatMap(_.asObject).map { auditObj =>
          val audits =
            auditObj.values.flatMap(_.as[Iterable[Audit]].toOption).flatten.toList
          audits.foldMap { audit =>
            AuditInfo(
              audit.level,
              Set(s"${AuditColors(audit.level)}: ${audit.category} (${audit.path})")
            )
          }
        }
      }.collect {
        case (id, Some(info)) => id -> info
      }
    }

  private def joinAuditInfo(
    idsToInfo: Map[String, AuditInfo]
  )(fileJson: JsonObject): JsonObject = {
    val info = fileJson(EncodeFields.EncodeIdField)
      .flatMap(_.asString)
      .flatMap(idsToInfo.get)
      .getOrElse(AuditInfo.mon.empty)

    fileJson
      .add(AuditColorField, AuditColors(info.maxLevel).asJson)
      .add(AuditWarningsField, info.summaries.asJson)
      .remove(EncodeFields.EncodeIdField)
  }
}

object JoinAuditsToFiles {

  val AuditColorField = "data_quality_category"
  val AuditWarningsField = "data_review_summary"

  private case class Audit(level: Int, category: String, path: String)

  private object Audit {
    implicit val dec: Decoder[Audit] = io.circe.derivation.deriveDecoder
  }

  private case class AuditInfo(maxLevel: Int, summaries: Set[String])

  private object AuditInfo {
    implicit val mon: Monoid[AuditInfo] = new Monoid[AuditInfo] {
      override val empty = AuditInfo(0, Set.empty)
      override def combine(x: AuditInfo, y: AuditInfo): AuditInfo =
        AuditInfo(math.max(x.maxLevel, y.maxLevel), x.summaries.union(y.summaries))
    }
  }

  val AuditColors = Map(
    0 -> "white",
    30 -> "white",
    40 -> "yellow",
    50 -> "orange",
    60 -> "red"
  )
}
