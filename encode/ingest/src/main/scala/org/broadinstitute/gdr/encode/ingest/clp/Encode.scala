package org.broadinstitute.gdr.encode.ingest.clp

import better.files.File
import caseapp.core.argparser.{ArgParser, SimpleArgParser}
import caseapp.core.commandparser.CommandParser
import caseapp.core.help.{CommandsHelp, Help, WithHelp}
import caseapp.core.parser.Parser
import cats.effect._
import cats.syntax.all._
import org.broadinstitute.gdr.encode.ingest.steps.IngestStep

/**
  * Lightweight CLI wrapper for demo ENCODE ingest logic.
  *
  * Reimplements a bunch of stuff from [[caseapp.core.app.CommandAppWithPreCommand]]
  * because that class doesn't play nicely with [[IOApp]].
  */
object Encode extends IOApp {

  implicit val fileParser: ArgParser[File] =
    SimpleArgParser.from("path")(s => Right(File(s)))

  private val programHelp: Help[None.type] =
    Help[None.type].copy(
      appName = "ENCODE ingest",
      progName = "encode-ingest",
      optionsDesc = "<command> <command-options...>"
    )

  private val commandsHelp: CommandsHelp[IngestCommand] =
    CommandsHelp[IngestCommand]

  private val commands: Seq[String] = commandsHelp.messages.map(_._1)

  private val parser: CommandParser[WithHelp[IngestCommand]] =
    CommandParser[IngestCommand].withHelp

  private def printHelp: IO[ExitCode] = {
    val message =
      s"""${programHelp.help}
         |Available commands: ${commands.mkString(", ")}
         |Use <command> --help for help on an individual command""".stripMargin

    printErr(message, ExitCode.Success)
  }

  private def printUsage: IO[ExitCode] = {
    val message =
      s"""${programHelp.usage}
         |Available commands: ${commands.mkString(", ")}
         |Use <command> --usage for usage on an individual command""".stripMargin

    printErr(message, ExitCode.Success)
  }

  private def printCommandHelp(command: String): IO[ExitCode] = printErr(
    commandsHelp.messagesMap(command).helpMessage(programHelp.progName, command),
    ExitCode.Success
  )

  private def printCommandUsage(command: String): IO[ExitCode] = printErr(
    commandsHelp.messagesMap(command).usageMessage(programHelp.progName, command),
    ExitCode.Success
  )

  private def printErr(
    message: String,
    retCode: ExitCode = ExitCode.Error
  ): IO[ExitCode] =
    IO(System.err.println(message)).as(retCode)

  override def run(args: List[String]): IO[ExitCode] =
    parser.parse(args)(Parser[None.type].withHelp) match {
      case Left(err) => printErr(err.message)
      case Right((_, extra, _)) if extra.nonEmpty =>
        printErr(s"Unknown arguments: ${extra.mkString(", ")}")
      case Right((WithHelp(usageAsked, helpAsked, _), _, maybeCmd)) =>
        if (usageAsked) {
          printUsage
        } else if (helpAsked) {
          printHelp
        } else {
          maybeCmd match {
            case Some(cmdParse) =>
              cmdParse match {
                case Left(err) => printErr(err.message)
                case Right((_, _, extra)) if extra.nonEmpty =>
                  printErr(s"Unknown arguments: ${extra.mkString(", ")}")
                case Right((command, WithHelp(usageAsked, helpAsked, baseOrError), _)) =>
                  if (helpAsked) {
                    printCommandHelp(command)
                  } else if (usageAsked) {
                    printCommandUsage(command)
                  } else {
                    baseOrError match {
                      case Left(err) => printErr(err.message)
                      case Right(cmd) =>
                        IngestStep
                          .blockingContext[IO]
                          .use(cmd.step(_).build[IO])
                          .as(ExitCode.Success)
                    }
                  }
              }
            case None =>
              printErr("No command given. Use --help to see available commands")
          }
        }
    }
}
