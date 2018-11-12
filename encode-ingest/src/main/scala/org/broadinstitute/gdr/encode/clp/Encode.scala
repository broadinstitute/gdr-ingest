package org.broadinstitute.gdr.encode.clp

import better.files.File
import caseapp.core.argparser.{ArgParser, SimpleArgParser}
import caseapp.core.commandparser.CommandParser
import caseapp.core.help.WithHelp
import cats.effect._
import cats.syntax.all._
import org.broadinstitute.gdr.encode.steps.IngestStep

/**
  * Lightweight CLI wrapper for demo ENCODE ingest logic.
  */
object Encode extends IOApp {

  implicit val fileParser: ArgParser[File] =
    SimpleArgParser.from("path")(s => Right(File(s)))

  private val parser: CommandParser[WithHelp[IngestCommand]] =
    CommandParser[IngestCommand].withHelp

  override def run(args: List[String]): IO[ExitCode] =
    parser.parse[None.type](args) match {
      case Left(err) => printErr(err.message)
      case Right((_, stuff, _)) if stuff.nonEmpty =>
        printErr(s"Unknown arguments: ${stuff.mkString(", ")}")
      case Right((_, _, maybeCmd)) =>
        maybeCmd match {
          case Some(cmdParse) =>
            cmdParse match {
              case Left(err) => printErr(err.message)
              case Right((_, _, stuff)) if stuff.nonEmpty =>
                printErr(s"Unknown arguments: ${stuff.mkString(", ")}")
              case Right((_, cmdOrHelp, _)) =>
                if (cmdOrHelp.help) {
                  IO(System.err.println("You asked for help")).as(ExitCode.Success)
                } else if (cmdOrHelp.usage) {
                  IO(System.err.println("You asked for usage")).as(ExitCode.Success)
                } else {
                  cmdOrHelp.baseOrError match {
                    case Left(err) => printErr(err.message)
                    case Right(cmd) =>
                      IngestStep
                        .blockingContext[IO]
                        .use(cmd.step(_).build[IO])
                        .as(ExitCode.Success)
                  }
                }
            }
          case None => printErr("No command given")
        }
    }

  private def printErr(message: String): IO[ExitCode] =
    IO(System.err.println(message)).as(ExitCode.Error)
}
