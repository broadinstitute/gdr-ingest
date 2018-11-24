package org.broadinstitute.gdr.encode.explorer.db

import cats.effect._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.hikari._
import doobie.util.ExecutionContexts
import doobie.util.log.{ExecFailure, ProcessingFailure, Success}

import scala.language.higherKinds

class DbClient[F[_]: Sync] private[db] (transactor: Transactor[F]) {

  private implicit val logHandler: LogHandler = {
    val logger = org.log4s.getLogger

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

  def fields(table: String): F[Set[String]] =
    sql"select column_name from information_schema.columns where table_name = $table"
      .query[String]
      .to[Set]
      .transact(transactor)

  def count(table: String): F[Long] =
    (fr"select count(*) from" ++ Fragment.const(table))
      .query[Long]
      .unique
      .transact(transactor)

  def countsByValue(table: String, column: String): F[List[(String, Long)]] = {
    Fragment
      .const0(
        s"""select $column, count(*)
           |from $table
           |where $column is not null
           |group by $column""".stripMargin
      )
      .query[(String, Long)]
      .to[List]
      .transact(transactor)
  }

  def countsByNestedValue(table: String, column: String): F[List[(String, Long)]] =
    Fragment
      .const0(
        s"""select v, count(*)
           |from (
           |  select unnest($column) as v
           |  from $table
           |  where $column is not null
           |) as v
           |group by v""".stripMargin
      )
      .query[(String, Long)]
      .to[List]
      .transact(transactor)

  def countsByRange(table: String, column: String): F[List[(String, Long)]] =
    Fragment
      .const0(
        s"""with source as (
           |  select $column from $table where $column is not null
           |), stats as (
           |  select min($column) as min, max($column) as max, count($column) as n from source
           |), histogram as (
           |  select
           |    case
           |      when stats.min = stats.max then 1
           |      else width_bucket($column, stats.min, stats.max, least(10, stats.n)::int)
           |    end as bucket,
           |    min($column) as low,
           |    max($column) as high,
           |    count($column) as freq
           |  from source, stats
           |  group by bucket
           |  order by bucket
           |)
           |select low, high, freq::bigint
           |from histogram""".stripMargin
      )
      .query[(Double, Double, Long)]
      .to[List]
      .transact(transactor)
      .map(_.map {
        case (low, high, count) =>
          val diff = high - low
          val range = if (diff == 0) {
            formatRangeEnd(low)
          } else {
            s"${formatRangeEnd(low)}-${formatRangeEnd(high)}"
          }
          range -> count
      })

  private def formatRangeEnd(n: Double): String =
    if (n == 0) {
      "0"
    } else if (n < 1) {
      n.formatted("%.5f")
    } else {
      n.round.toString
    }
}

object DbClient {

  def resource[F[_]: ContextShift: Async](config: DbConfig): Resource[F, DbClient[F]] =
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
