package org.broadinstitute.gdr.encode.steps

import better.files.File
import cats.effect.Effect
import fs2.Stream
import org.broadinstitute.gdr.encode.client.EncodeClient

import scala.language.higherKinds

class SearchExperimentIds(out: File) extends IngestStep {
  override def run[F[_]: Effect]: F[Unit] = {
    val idStream = EncodeClient
      .stream[F]
      .flatMap { client =>
        client.search(
          // Only pull publicly-available data.
          "status" -> "released",
          // Pull everything.
          "limit" -> "all",
          // Make sure search returns properly-formatted data.
          "frame" -> "object",
          "format" -> "json",
          // Pull all ChIP-seq experiments.
          "type" -> "Experiment",
          "assay_term_name" -> "ChIP-seq"
        )
      }
      .evalMap { json =>
        Effect[F].fromEither(
          json.hcursor.get[String]("@id")
        )
      }

    idStream
      .intersperse("\n")
      .flatMap(str => Stream.fromIterator(str.getBytes.iterator))
      .to(fs2.io.file.writeAll(out.path))
      .compile
      .drain
  }
}
