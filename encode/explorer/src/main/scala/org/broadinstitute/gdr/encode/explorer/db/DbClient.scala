package org.broadinstitute.gdr.encode.explorer.db

import cats.data.NonEmptyList
import cats.effect._
import cats.free.Free
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.hikari._
import doobie.postgres.circe.json.implicits._
import doobie.postgres.implicits._
import doobie.util.ExecutionContexts
import doobie.util.log.{ExecFailure, ProcessingFailure, Success}
import io.circe.Json
import org.broadinstitute.gdr.encode.explorer.export.ExportJson
import org.broadinstitute.gdr.encode.explorer.facets.Facet
import org.broadinstitute.gdr.encode.explorer.facets.Facet.{KeywordFacet, RangeFacet}
import org.broadinstitute.gdr.encode.explorer.fields.{FieldConfig, FieldFilter, FieldType}

import scala.language.higherKinds

/**
  * Component responsible for building / executing DB queries.
  *
  * @tparam F wrapper type capable of suspending synchronous effects
  * @param transactor wrapper around a source of DB connections which
  *                   can actually run SQL
  *
  * @see https://tpolecat.github.io/doobie/docs/01-Introduction.html for
  *      documentation on `doobie`, the library used for DB access
  */
class DbClient[F[_]: Sync] private[db] (transactor: Transactor[F]) {

  private val logger = org.log4s.getLogger

  private implicit val logHandler: LogHandler = {

    def fmtSql(sql: String, padding: String): String =
      sql.lines.filter(_.trim.nonEmpty).mkString(padding, s"\n$padding", "")

    LogHandler {
      case Success(sql, args, exec, processing) =>
        logger.info(s"""Successfully executed statement:
             |${fmtSql(sql, "  ")}
             |
             |       Arguments: [${args.mkString(", ")}]
             |  Execution time: ${exec.toMillis} ms
             | Processing time: ${processing.toMillis} ms
           """.stripMargin)

      case ExecFailure(sql, args, exec, failure) =>
        logger.error(s"""Failed to execute statement:
             |${fmtSql(sql, "  ")}
             |
             |       Arguments: [${args.mkString(", ")}]
             |  Execution time: ${exec.toMillis} ms
             |         Failure: ${failure.getMessage}
           """.stripMargin)

      case ProcessingFailure(sql, args, exec, processing, failure) =>
        logger.error(s"""Failed to process result set from statement:
           |${fmtSql(sql, "  ")}
           |
           |       Arguments: [${args.mkString(", ")}]
           |  Execution time: ${exec.toMillis} ms
           | Processing time: ${processing.toMillis} ms
           |         Failure: ${failure.getMessage}
         """.stripMargin)
    }
  }

  /** Check that configuration for fields reflect actual columns in a DB table. */
  def validateFields(allFields: List[FieldConfig]): F[Unit] =
    allFields.groupBy(_.table).toList.traverse_ {
      case (table, fields) =>
        fieldTypes(table).flatMap { dbFields =>
          /*
           * FIXME: traverse_ fails fast as long as `validateField` returns `F`.
           * Changing `validateField` to return a `ValidatedNel` should collect the
           * errors, but then custom logic is needed to lift the result into an `F`.
           */
          fields.traverse_(validateField(dbFields))
        }
    }

  /** Check that configuration for a single field reflects an actual DB column. */
  private def validateField(dbTypes: Map[String, String])(field: FieldConfig): F[Unit] = {
    val col = field.column
    (dbTypes.get(col), field.fieldType) match {
      case (None, _) =>
        val err: Throwable = new IllegalStateException(s"No such field: $col")
        err.raiseError[F, Unit]
      case (Some(tpe), fTpe) if fTpe.matches(tpe) => ().pure[F]
      case (Some(tpe), fTpe) =>
        val err: Throwable = new IllegalStateException(
          s"Field '$col' has DB type '$tpe', but is configured as type '${fTpe.entryName}'"
        )
        err.raiseError[F, Unit]
    }
  }

  /** Get the DB types of all columns in a table. */
  private def fieldTypes(table: DbTable): F[Map[String, String]] =
    sql"select column_name, data_type from information_schema.columns where table_name = ${table.entryName}"
      .query[(String, String)]
      .to[Set]
      .map(_.toMap)
      .transact(transactor)

  /** Get the count of all elements in a table. */
  def countRows(table: DbTable, filters: Map[FieldConfig, Fragment]): F[Long] = {
    val selectFrom = Fragment.const(s"select count(*) from ${table.entryName}")
    val whereFilters =
      Fragments.whereAnd(filters.filter(_._1.table == table).values.toSeq: _*)

    (selectFrom ++ whereFilters)
      .query[Long]
      .unique
      .transact(transactor)
  }

  /** Get the counts of each facet value for a field in a table under a set of filters. */
  def buildFacet(field: FieldConfig): F[Facet] = {
    val query = field.fieldType match {
      case FieldType.Keyword => listFacet(field)
      case FieldType.Boolean => boolFacet(field)
      case FieldType.Array   => nestedFacet(field)
      case FieldType.Number  => rangeFacet(field)
    }

    query.transact(transactor)
  }

  /** Get the unique values in a column. */
  private def listFacet(field: FieldConfig): ConnectionIO[Facet] = {
    val col = Fragment.const(field.column)
    val tbl = Fragment.const(field.table.entryName)

    (fr"select distinct " ++ col ++ fr"from " ++ tbl ++ fr"where " ++ col ++ fr"is not null order by " ++ col)
      .query[String]
      .to[List]
      .map(KeywordFacet(field.displayName, field.encoded, _))
  }

  /** Get the counts of each boolean value in a column. */
  private def boolFacet(field: FieldConfig): ConnectionIO[Facet] = Free.pure(
    KeywordFacet(
      field.displayName,
      field.encoded,
      List(DbClient.TrueRepr, DbClient.FalseRepr)
    )
  )

  /** Get the counts of each unique nested value in an array column. */
  private def nestedFacet(field: FieldConfig): ConnectionIO[Facet] = {
    val col = Fragment.const(field.column)
    val tbl = Fragment.const(field.table.entryName)

    val selectUnnestFrom =
      fr"select unnest(" ++ col ++ fr") as v from " ++ tbl ++ fr"where " ++ col ++ fr"is not null"

    (fr"select distinct v from (" ++ selectUnnestFrom ++ fr") as v order by v")
      .query[String]
      .to[List]
      .map(KeywordFacet(field.displayName, field.encoded, _))
  }

  /** Get the min, max, and count of elements in a numeric column. */
  private def rangeFacet(field: FieldConfig): ConnectionIO[Facet] = {
    val col = Fragment.const(field.column)
    val tbl = Fragment.const(field.table.entryName)

    (fr"select min(" ++ col ++ fr"), max(" ++ col ++ fr") from" ++ tbl ++ fr"where" ++ col ++ fr"is not null")
      .query[(Double, Double)]
      .unique
      .map { case (min, max) => RangeFacet(field.displayName, field.encoded, min, max) }
  }

  /** Convert the `FieldFilter`s in a filter map into corresponding SQL constraints. */
  def filtersToSql(allFilters: FieldFilter.Filters): F[Map[FieldConfig, Fragment]] = {
    allFilters.toList.traverse {
      case (field, values) => filterToSql(field, values).map(field -> _)
    }.map { baseFilters =>
      val donorFilters = baseFilters.filter(_._1.table == DbTable.Donors).toMap
      val fileFilters = baseFilters.filter(_._1.table == DbTable.Files).toMap

      // If we've filtered out all files for a donor, filter out the donor too.
      val allDonorFilters = if (fileFilters.isEmpty) {
        donorFilters
      } else {
        donorFilters + (DbClient.DonorIdField -> donorFromIncludedFile(
          fileFilters.values
        ))
      }

      // If we've filtered out all source donors for a file, filter out the file too.
      val allFileFilters = if (donorFilters.isEmpty) {
        fileFilters
      } else {
        fileFilters + (DbClient.DonorsIdField -> fileFromIncludedDonor(
          donorFilters.values
        ))
      }

      allDonorFilters ++ allFileFilters
    }
  }

  /** Build a SQL constraint which checks that a field matches one of a set of filters. */
  private def filterToSql(field: FieldConfig, filter: FieldFilter): F[Fragment] = {
    import FieldFilter.{KeywordFilter, RangeFilter}

    val constraint = (field.fieldType, filter) match {
      case (FieldType.Keyword, KeywordFilter(values)) =>
        whereKeywordOneOf(field.column, values).pure[F]
      case (FieldType.Boolean, KeywordFilter(values)) =>
        whereBoolOneOf(field.column, values).pure[F]
      case (FieldType.Array, KeywordFilter(values)) =>
        whereArrayIntersects(field.column, values).pure[F]
      case (FieldType.Number, RangeFilter(low, high)) =>
        whereNumberInRange(field.column, low, high).pure[F]
      case _ =>
        new IllegalArgumentException(s"Invalid filter given for field ${field.column}")
          .raiseError[F, Fragment]
    }

    constraint.map(fr"(" ++ _ ++ fr")")
  }

  /**
    * Build a SQL constraint on the [[DbTable.Donors]] table which will remove all rows
    * that will not be referred to by any files after the given filters are applied to the
    * [[DbTable.Files]] table.
    */
  private def donorFromIncludedFile(fileFilters: Iterable[Fragment]): Fragment = {
    val donorIds = fr"select unnest(" ++
      Fragment.const0(DbClient.FileDonorsFk) ++
      fr") from " ++
      Fragment.const(DbTable.Files.entryName) ++
      Fragments.whereAnd(fileFilters.toSeq: _*)

    Fragment.const(DbClient.DonorIdColumn) ++ fr"IN (" ++ donorIds ++ fr")"
  }

  /**
    * Build a SQL constraint on the [[DbTable.Files]] table which will remove all rows
    * that refer only to donors which will be filtered out after the given filters are
    * applied to the [[DbTable.Donors]] table.
    */
  private def fileFromIncludedDonor(donorFilters: Iterable[Fragment]): Fragment = {
    val donorIds = fr"select array_agg(donor_id) from " ++
      Fragment.const(DbTable.Donors.entryName) ++
      Fragments.whereAnd(donorFilters.toSeq: _*)

    Fragment.const(DbClient.FileDonorsFk) ++ fr"&& (" ++ donorIds ++ fr")"
  }

  /** Build a SQL constraint checking that a string column is one of a set of keywords. */
  private def whereKeywordOneOf(column: String, values: NonEmptyList[String]): Fragment =
    Fragments.in(Fragment.const(column), values)

  /** Build a SQL constraint checking that a bool column is one of a set of booleans. */
  private def whereBoolOneOf(column: String, values: NonEmptyList[String]): Fragment =
    Fragments.in(Fragment.const(column), values.map(_ == DbClient.TrueRepr))

  /** Build a SQL constraint checking that an array column overlaps a set of keywords. */
  private def whereArrayIntersects(
    column: String,
    values: NonEmptyList[String]
  ): Fragment =
    Fragment.const(s"$column &&") ++ fr"${values.toList}"

  /** Build a SQL constraint checking that a numeric column falls within a range. */
  private def whereNumberInRange(
    column: String,
    low: Double,
    high: Double
  ): Fragment = {
    val col = Fragment.const(column)
    col ++ fr">= $low AND " ++ col ++ fr"<= $high"
  }

  /** Collect JSON for all donors matching the given filters, for export to Terra. */
  def getDonorJson(filters: Map[FieldConfig, Fragment]): F[Vector[ExportJson]] =
    getEntityJson("donor", DbClient.DonorIdColumn, DbTable.Donors, filters)

  /** Collect JSON for all files matching the given filters, for export to Terra. */
  def getFileJson(filters: Map[FieldConfig, Fragment]): F[Vector[ExportJson]] =
    getEntityJson("file", DbClient.FileIdColumn, DbTable.Files, filters)

  /** Collect JSON for all entities matching the given filters, for export to Terra. */
  private def getEntityJson(
    entityType: String,
    nameField: String,
    table: DbTable,
    allFilters: Map[FieldConfig, Fragment]
  ): F[Vector[ExportJson]] = {
    val filters = allFilters.filter(_._1.table == table).values

    val source = Fragment.const(s"select * from ${table.entryName}") ++
      Fragments.whereAnd(filters.toSeq: _*)

    (fr"select row_to_json(res) from (" ++ source ++ fr") as res")
      .query[Json]
      .stream
      .transact(transactor)
      .evalMap { js =>
        val parsed = for {
          obj <- js.asObject
          id <- obj(nameField)
        } yield {
          ExportJson(id, entityType, obj.remove(nameField))
        }

        parsed.liftTo[F](new IllegalStateException(s"Failed to parse JSON: $js"))
      }
      .compile
      .toVector
  }
}

object DbClient {

  /**
    * Value to use for boolean 'true' in responses to the UI.
    *
    * By default, Postgres returns "t" for true.
    */
  val TrueRepr = "yes"

  /**
    * Value to use for boolean 'false' in responses to the UI.
    *
    * By default, Postgres returns "f" for false.
    */
  val FalseRepr = "no"

  /** Primary-key column in the donors table. */
  val DonorIdColumn = "donor_id"

  /** Configuration for the primary-key column in the donors table. */
  val DonorIdField = FieldConfig(
    DbTable.Donors,
    DonorIdColumn,
    "Donor ID",
    FieldType.Keyword
  )

  /** Primary-key column in the files table. */
  val FileIdColumn = "file_id"

  /** Foreign-key column in the files table, pointing to the donors table. */
  val FileDonorsFk = "donor_ids"

  /** Configuration for the foreign-key column in the files table pointing to the donors table. */
  val DonorsIdField = FieldConfig(
    DbTable.Files,
    FileDonorsFk,
    "Donor ID",
    FieldType.Array
  )

  /**
    * Construct a DB client, wrapped in logic which will:
    *   1. Automatically spin up a connection pool on startup, and
    *   2. Automatically clean up the connection pool on shutdown
    *
    * @param config settings pointing to the DB the client should run against
    * @tparam F wrapper type capable of:
    *           1. Suspending asynchronous effects, and
    *           2. Shifting computations back and forth between thread pools
    */
  def resource[F[_]: Async: ContextShift](config: DbConfig): Resource[F, DbClient[F]] =
    for {
      connectionContext <- ExecutionContexts.fixedThreadPool[F](
        org.http4s.blaze.channel.DefaultPoolSize
      )
      transactionContext <- ExecutionContexts.cachedThreadPool[F]
      transactor <- HikariTransactor.newHikariTransactor[F](
        config.driverClassname,
        config.connectURL,
        config.username,
        config.password,
        connectionContext,
        transactionContext
      )
    } yield {
      new DbClient(transactor)
    }
}
