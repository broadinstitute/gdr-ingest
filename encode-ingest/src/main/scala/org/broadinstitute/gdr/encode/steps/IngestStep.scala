package org.broadinstitute.gdr.encode.steps

import better.files.File
import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2.{Sink, Stream}
import io.circe.Json

import scala.language.higherKinds

trait IngestStep {
  protected val logger = org.log4s.getLogger

  protected def process[F[_]: Effect]: Stream[F, Unit]

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

  protected def out: File
}

object IngestStep {

  def readJsonArray[F[_]: Sync](in: File): Stream[F, Json] =
    fs2.io.file
      .readAll(in.path, 8192)
      .through(io.circe.fs2.byteArrayParser)

  def writeJsonArray[F[_]: Sync](out: File): Sink[F, Json] = jsons => {
    val byteStream =
      jsons.map(_.noSpaces).intersperse(",").flatMap(str => Stream.emits(str.getBytes))

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
}
