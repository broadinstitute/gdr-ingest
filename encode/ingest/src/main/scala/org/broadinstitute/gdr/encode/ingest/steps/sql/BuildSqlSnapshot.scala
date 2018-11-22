package org.broadinstitute.gdr.encode.ingest.steps.sql

import better.files.File
import cats.effect.{ConcurrentEffect, ContextShift, Sync, Timer}
import fs2.Stream
import io.circe.{Json, JsonObject}
import org.broadinstitute.gdr.encode.ingest.steps.{ExportTransforms, IngestStep}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class BuildSqlSnapshot(
  filesMetadata: File,
  donorsMetadata: File,
  storageBucket: String,
  override protected val out: File,
  ec: ExecutionContext
) extends IngestStep {
  val _ = (filesMetadata, donorsMetadata, storageBucket, ec)

  override def process[
    F[_]: ConcurrentEffect: Timer: ContextShift
  ]: Stream[F, Unit] =
    insertStatements[F](donorsMetadata, "donors")
      .append(
        insertStatements[F](
          filesMetadata,
          "files",
          ExportTransforms.swapFileFields(storageBucket)
        )
      )
      .to(IngestStep.writeLines(ec)(out))

  private def insertStatements[F[_]: Sync: ContextShift](
    metadataFile: File,
    tableName: String,
    transform: JsonObject => JsonObject = identity
  ): Stream[F, String] =
    Stream
      .emit(s"TRUNCATE TABLE $tableName;")
      .append(
        IngestStep
          .readJsonArray(ec)(metadataFile)
          .map(transform)
          .map(insertStatement(tableName))
      )

  private def insertStatement(table: String)(metadata: JsonObject): String = {
    val (columns, values) = metadata.toList.unzip
    val prettyValues = values.map(jsonToSql(_)).mkString(",")

    s"INSERT INTO $table (${columns.mkString(",")}) VALUES ($prettyValues);"
  }

  private def jsonToSql(js: Json, quote: String = "'"): String = js.fold(
    jsonNull = "NULL",
    jsonBoolean = _.toString,
    jsonNumber = _.toString,
    jsonString = str => s"$quote${str.replaceAll(quote, s"$quote$quote")}$quote",
    jsonArray = arr => s"$quote{${arr.map(jsonToSql(_, "\"")).mkString(",")}}$quote",
    jsonObject = _ => throw new IllegalArgumentException(s"Nested object found: $js")
  )
}
