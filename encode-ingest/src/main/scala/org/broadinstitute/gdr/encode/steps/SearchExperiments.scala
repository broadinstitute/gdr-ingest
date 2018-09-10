package org.broadinstitute.gdr.encode.steps

import better.files.File
import cats.effect.Effect
import fs2.Stream
import org.broadinstitute.gdr.encode.client.EncodeClient

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class SearchExperiments(out: File)(implicit ec: ExecutionContext) extends IngestStep {
  override def run[F[_]: Effect]: F[Unit] = {
    val metadataStream = EncodeClient
      .stream[F]
      .flatMap { client =>
        client.search(
          // Pull all publicly-released ChIP-seq experiments.
          "type" -> "Experiment",
          "assay_term_name" -> "ChIP-seq",
          "status" -> "released"
        )
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
}
