package org.broadinstitute.gdr.encode.explorer.db

import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.Lowercase

/** A table that can be queried in the DB backing the API. */
sealed trait DbTable extends EnumEntry with Lowercase

object DbTable extends Enum[DbTable] {
  override val values = findValues

  case object Donors extends DbTable
  case object Files extends DbTable
}
