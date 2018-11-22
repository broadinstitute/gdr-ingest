package org.broadinstitute.gdr.encode.explorer.db

import cats.Monad
import cats.effect._
import cats.implicits._
import fs2.Stream
import doobie._
import doobie.implicits._
import doobie.hikari._
import doobie.util.ExecutionContexts

import scala.language.higherKinds

class DbClient[F[_]: Monad] private[db] (transactor: Transactor[F]) {

  def count(table: String): F[Long] =
    Query0[Long](s"select count(*) from $table").unique.transact(transactor)

  def countsByValue(table: String, column: String): Stream[F, (String, Long)] =
    Query0[(String, Long)](
      s"""select $column, count(*)
         |from $table
         |where $column is not null
         |group by $column""".stripMargin
    ).stream.transact(transactor)
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