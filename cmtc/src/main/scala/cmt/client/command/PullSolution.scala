package cmt.client.command

import caseapp.{AppName, CommandName, HelpMessage, Recurse, RemainingArgs}
import cmt.{CMTcConfig, CmtError, printResult, toConsoleGreen}
import cmt.Helpers.{fileList, withZipFile}
import cmt.client.cli.SharedOptions
import cmt.client.command.{deleteCurrentState, getCurrentExerciseId}
import cmt.core.CmtCommand
import cmt.core.execution.Executable
import cmt.core.validation.Validatable
import sbt.io.IO as sbtio
import sbt.io.syntax.*
import cmt.core.enforceNoTrailingArguments

object PullSolution:

  @AppName("pull-solution")
  @CommandName("pull-solution")
  @HelpMessage("Pull in all code for the active exercise. All local changes are discarded")
  final case class Options(@Recurse shared: SharedOptions)

  given Validatable[PullSolution.Options] with
    extension (options: PullSolution.Options)
      def validated(): Either[CmtError, PullSolution.Options] =
        Right(options)
      end validated
  end given

  given Executable[PullSolution.Options] with
    extension (cmd: PullSolution.Options)
      def execute(): Either[CmtError, String] = {
        val config = new CMTcConfig(cmd.shared.studentifiedRepo.value)
        val currentExerciseId = getCurrentExerciseId(config.bookmarkFile)

        deleteCurrentState(cmd.shared.studentifiedRepo.value)(config)

        withZipFile(config.solutionsFolder, currentExerciseId) { solution =>
          val files = fileList(solution / currentExerciseId)
          sbtio.copyDirectory(
            config.solutionsFolder / currentExerciseId,
            config.activeExerciseFolder,
            preserveLastModified = true)
          Right(toConsoleGreen(s"Pulled solution for $currentExerciseId"))
        }
      }

  val command = new CmtCommand[PullSolution.Options] {

    def run(options: PullSolution.Options, args: RemainingArgs): Unit =
      args.enforceNoTrailingArguments().flatMap(_ => options.validated().flatMap(_.execute())).printResult()
  }

end PullSolution
