package org.broadinstitute.gdr.encode.steps.download

import better.files.File
import cats.effect.Sync
import cats.implicits._
import fs2.{Pipe, Pure, Scheduler, Stream}
import org.broadinstitute.gdr.encode.steps.IngestStep

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/**
  * Ingest step for pulling raw metadata from ENCODE, based on references found in already-downloaded metadata.
  *
  * @param in path to a JSON array of metadata previously downloaded from ENCODE
  */
abstract class GetFromPreviousMetadataStep(in: File, out: File)(
  implicit ec: ExecutionContext,
  s: Scheduler
) extends GetMetadataStep(out) {

  /** Field containing ref values to query in the objects read from `in`. */
  def refField: String

  /** If `true`, expect `refField` to point to an array of references instead of a single reference. */
  def manyRefs: Boolean

  final override def searchParams[F[_]: Sync]: Stream[F, List[(String, String)]] =
    IngestStep
      .readJsonArray(in)
      .flatMap { obj =>
        val refs = for {
          refJson <- obj(refField).toRight(
            new IllegalStateException(
              s"Expected field $refField not found in object $obj"
            )
          )
          refValues <- if (manyRefs) {
            refJson.as[Seq[String]].map(Stream.emits)
          } else {
            refJson.as[String].map(Stream.emit)
          }
        } yield {
          refValues
        }

        refs.valueOr(Stream.raiseError).covary[F]
      }
      .through(filterRefs)
      // Batch into groups of 100 based on guess-and-check.
      // Sending much larger of a batch size sometimes causes "URI too long" errors.
      .segmentN(100)
      .map {
        _.fold(List.empty[(String, String)])((acc, ref) => ("@id" -> ref) :: acc).force.run._2
      }

  /**
    * Pipe which emits only distinct elements from upstream.
    *
    * NOTE: This only emits when the upstream completes; will OOM on huge source streams.
    */
  private val filterRefs: Pipe[Pure, String, String] =
    _.fold(Set.empty[String])(_ + _).flatMap(s => Stream.emits(s.toSeq))
}
