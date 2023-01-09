package cmt.client.command

import caseapp.{AppName, CommandName, HelpMessage, Recurse, RemainingArgs}
import cmt.client.cli.SharedOptions
import cmt.{CMTcConfig, CmtError, printResult, toConsoleGreen, toConsoleYellow}
import cmt.core.CmtCommand
import cmt.core.validation.Validatable
import sbt.io.IO as sbtio
import cmt.core.enforceNoTrailingArguments

object ListSavedStates:

  @AppName("list-saved-states")
  @CommandName("list-saved-states")
  @HelpMessage("List all saved exercise states, if any.")
  final case class Options(@Recurse shared: SharedOptions)

  given Validatable[ListSavedStates.Options] with
    extension (options: ListSavedStates.Options)
      def validated(): Either[CmtError, ListSavedStates.Options] =
        Right(options)
      end validated
  end given

  extension (options: ListSavedStates.Options)
    def execute(): Either[CmtError, String] = {
      val config = new CMTcConfig(options.shared.studentifiedRepo.value)
      val MatchDotzip = ".zip".r
      val savedStates =
        sbtio
          .listFiles(config.studentifiedSavedStatesFolder)
          .to(List)
          .sorted
          .map(_.getName)
          .map(item => MatchDotzip.replaceAllIn(item, ""))
          .filter(config.exercises.contains(_))

      Right(
        toConsoleGreen(s"Saved states available for exercises:\n") + toConsoleYellow(
          s"${savedStates.mkString("\n   ", "\n   ", "\n")}"))
    }

  val command = new CmtCommand[ListSavedStates.Options] {

    def run(options: ListSavedStates.Options, args: RemainingArgs): Unit =
      args.enforceNoTrailingArguments().flatMap(_ => options.validated().flatMap(_.execute())).printResult()
  }

end ListSavedStates
