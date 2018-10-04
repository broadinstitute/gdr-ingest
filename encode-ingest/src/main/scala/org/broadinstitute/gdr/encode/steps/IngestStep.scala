package org.broadinstitute.gdr.encode.steps

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.{Sink, Stream}
import io.circe.syntax._
import io.circe.JsonObject
import org.broadinstitute.gdr.encode.EncodeFields

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/**
  * Distinct step in the ENCODE ingest pipeline.
  *
  * In vague terms, steps should end at whatever point it makes sense
  * to dump intermediate results to disk for caching.
  */
trait IngestStep {
  protected val logger = org.log4s.getLogger

  /**
    * Run step-specific logic.
    *
    * Steps are responsible for persisting their own outputs as an effect (i.e. writing them to disk).
    */
  protected def process[F[_]: Effect]: Stream[F, Unit]

  /**
    * Convenience wrapper for packaging up custom stream logic into a runnable effect.
    *
    * Does some hacky caching that should probably get ripped out sooner rather than later.
    */
  final def build[F[_]: Effect]: F[Unit] =
    Effect[F].delay(out.isRegularFile).flatMap { outputExists =>
      if (outputExists) {
        Effect[F].delay(
          logger.warn(s"Skipping step ${getClass.getName}, output $out already exists")
        )
      } else {
        process.compile.drain
      }
    }

  /** File to dump step outputs into. */
  protected def out: File
}

object IngestStep {

  def readJsonArray[F[_]: Sync](in: File): Stream[F, JsonObject] =
    fs2.io.file
      .readAll(in.path, 8192)
      .through(io.circe.fs2.byteArrayParser)
      .map(_.as[JsonObject])
      .rethrow

  /** Slurp a JSON array of metadata into an in-memory map from ID -> fields. */
  def readLookupTable[F[_]: Sync](
    metadata: File,
    idField: String = EncodeFields.EncodeIdField
  ): F[Map[String, JsonObject]] =
    IngestStep
      .readJsonArray(metadata)
      .map { js =>
        js(idField)
          .flatMap(_.asString)
          .map(_ -> js.remove(idField))
      }
      .unNone
      .compile
      .fold(Map.empty[String, JsonObject])(_ + _)

  def writeJsonArray[F[_]: Sync](out: File): Sink[F, JsonObject] = jsons => {
    val byteStream =
      jsons
        .map(_.asJson.noSpaces)
        .intersperse(",")
        .flatMap(str => Stream.emits(str.getBytes))

    Stream
      .emit('['.toByte)
      .append(byteStream)
      .append(Stream.emit(']'.toByte))
      .to(fs2.io.file.writeAll(out.path))
  }

  def writeLines[F[_]: Sync](out: File): Sink[F, String] =
    _.intersperse("\n")
      .flatMap(str => Stream.emits(str.getBytes))
      .to(fs2.io.file.writeAll(out.path))

  def parallelize[F[_]: Effect](steps: IngestStep*)(
    implicit ec: ExecutionContext
  ): F[Unit] =
    Stream
      .emits(steps)
      .map(_.build[F])
      .map(Stream.eval)
      .joinUnbounded
      .compile
      .drain
}
