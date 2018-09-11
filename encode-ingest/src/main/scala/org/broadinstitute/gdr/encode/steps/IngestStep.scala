package org.broadinstitute.gdr.encode.steps

import better.files.File
import cats.effect.{Effect, Sync}
import fs2.Stream
import io.circe.Json

import scala.language.higherKinds

trait IngestStep {
  def run[F[_]: Effect]: F[Unit]
}

object IngestStep {

  def readJsonArray[F[_]: Sync](in: File): Stream[F, Json] =
    fs2.io.file
      .readAll(in.path, 8192)
      .through(io.circe.fs2.byteArrayParser)

  def writeJsonArray[F[_]: Sync](out: File): fs2.Sink[F, Json] = jsons => {
    val byteStream =
      jsons.map(_.noSpaces).intersperse(",").flatMap(str => Stream.emits(str.getBytes))

    Stream
      .emit('['.toByte)
      .append(byteStream)
      .append(Stream.emit(']'.toByte))
      .to(fs2.io.file.writeAll(out.path))
  }
}
