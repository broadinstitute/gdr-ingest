package org.broadinstitute.gdr.encode.steps

import cats.effect.Effect

import scala.language.higherKinds

trait IngestStep {
  def run[F[_]: Effect]: F[Unit]
}
