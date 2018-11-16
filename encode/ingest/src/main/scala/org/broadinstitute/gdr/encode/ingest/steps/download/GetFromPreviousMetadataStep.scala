package org.broadinstitute.gdr.encode.ingest.steps.download

import better.files.File
import cats.effect.{ContextShift, Sync}
import cats.syntax.all._
import fs2.{Pipe, Pure, Stream}
import org.broadinstitute.gdr.encode.ingest.EncodeFields
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/**
  * Ingest step for pulling raw metadata from ENCODE, based on references found in already-downloaded metadata.
  *
  * @param in path to a JSON array of metadata previously downloaded from ENCODE
  */
abstract class GetFromPreviousMetadataStep(in: File, out: File, ec: ExecutionContext)
    extends GetMetadataStep(out, ec) {

  /** Field containing ref values to query in the objects read from `in`. */
  def refField: String

  /** If `true`, expect `refField` to point to an array of references instead of a single reference. */
  def manyRefs: Boolean

  final override def searchParams[
    F[_]: Sync: ContextShift
  ]: Stream[F, List[(String, String)]] =
    IngestStep
      .readJsonArray(blockingEc)(in)
      .map(_(refField))
      .unNone
      .flatMap { refJson =>
        val refs = for {
          refValues <- if (manyRefs) {
            refJson.as[Seq[String]].map(Stream.emits)
          } else {
            refJson.as[String].map(Stream.emit)
          }
        } yield {
          refValues
        }

        refs.valueOr(Stream.raiseError[F])
      }
      .through(filterRefs)
      // Batch into groups of 100 based on guess-and-check.
      // Sending much larger of a batch size sometimes causes "URI too long" errors.
      .chunkN(100)
      .map {
        _.foldLeft(List.empty[(String, String)]) { (acc, ref) =>
          (EncodeFields.EncodeIdField -> ref) :: acc
        }
      }

  /**
    * Pipe which emits only distinct elements from upstream.
    *
    * NOTE: This only emits when the upstream completes; will OOM on huge source streams.
    */
  private val filterRefs: Pipe[Pure, String, String] =
    _.fold(Set.empty[String])(_ + _).flatMap(s => Stream.emits(s.toSeq))
}
