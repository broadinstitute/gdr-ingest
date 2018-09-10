package org.broadinstitute.gdr.encode.steps.metadata

import better.files.File
import cats.effect.{Effect, Sync}
import fs2.{Pipe, Stream}
import org.broadinstitute.gdr.encode.client.EncodeClient
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

abstract class GetMetadataStep(out: File)(implicit ec: ExecutionContext)
    extends IngestStep {

  final override def run[F[_]: Effect]: F[Unit] = {
    val metadataStream = EncodeClient.stream[F].flatMap { client =>
      searchParams.map { params =>
        client.search(("type" -> entityType) :: params)
      }.join(EncodeClient.Parallelism)
    }

    val byteStream = metadataStream.map(_.noSpaces).intersperse(",").flatMap { str =>
      Stream.emits(str.getBytes)
    }

    Stream
      .emit('['.toByte)
      .append(byteStream)
      .append(Stream.emit(']'.toByte))
      .to(fs2.io.file.writeAll(out.path))
      .compile
      .drain
  }

  def entityType: String

  def searchParams[F[_]: Sync]: Stream[F, List[(String, String)]]
}

object GetMetadataStep {

  def uniquePipe[F[_]]: Pipe[F, String, String] =
    _.fold(Set.empty[String])(_ + _).flatMap(s => Stream.emits(s.toSeq))
}
