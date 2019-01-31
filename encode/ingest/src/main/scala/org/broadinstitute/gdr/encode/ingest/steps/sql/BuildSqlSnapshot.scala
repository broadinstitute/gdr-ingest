package org.broadinstitute.gdr.encode.ingest.steps.sql

import better.files.File
import cats.effect.{ConcurrentEffect, ContextShift, Sync, Timer}
import fs2.Stream
import io.circe.{Json, JsonObject}
import org.broadinstitute.gdr.encode.ingest.steps.{ExportTransforms, IngestStep}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/**
  * Ingest step which transforms cleaned / shaped metadata into SQL INSERT statements
  * that can be loaded into the Data Explorer API's backend.
  *
  * @param filesMetadata path to files-with-uris output from the 'prepare-metadata' command
  * @param donorsMetadata path to cleaned-donors output from the 'prepare-metadata' command
  * @param storageBucket GCS bucket containing the raw data described by `filesMetadata`
  * @param out path to output file where generated SQL should be written
  * @param ec execution context which should run blocking I/O operations
  */
class BuildSqlSnapshot(
  filesMetadata: File,
  donorsMetadata: File,
  storageBucket: String,
  override protected val out: File,
  ec: ExecutionContext
) extends IngestStep {

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
      .through(IngestStep.writeLines(ec)(out))

  /**
    * Build a stream of SQL statements which will populate a DB table with
    * the metadata contained in a JSON file.
    *
    * @tparam F wrapper type capable of:
    *           1. suspending synchronous effects, and
    *           2. shifting computations onto a separate thread pool
    * @param metadataFile file containing metadata to load
    * @param tableName table to load with metadata
    * @param transform transformation to apply to JSON before transforming it into SQL
    */
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

  /** Build a SQL INSERT statement which will load a JSON object into a DB table. */
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
