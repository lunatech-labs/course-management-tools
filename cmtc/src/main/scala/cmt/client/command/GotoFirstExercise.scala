package cmt.client.command

import caseapp.{Recurse, RemainingArgs}
import cmt.{CMTcConfig, CmtError, printResult}
import cmt.client.Domain.{ExerciseId, ForceMoveToExercise, StudentifiedRepo}
import cmt.client.cli.SharedOptions
import cmt.core.CmtCommand
import cmt.core.execution.Executable
import cmt.core.validation.Validatable
import cmt.client.cli.ArgParsers.{forceMoveToExerciseArgParser, studentifiedRepoArgParser}

object GotoFirstExercise:

  final case class Options(
      studentifiedRepo: StudentifiedRepo,
      force: ForceMoveToExercise = ForceMoveToExercise(false),
      @Recurse shared: SharedOptions)

  given Validatable[GotoFirstExercise.Options] with
    extension (options: GotoFirstExercise.Options)
      def validated(): Either[CmtError, GotoFirstExercise.Options] =
        Right(options)
      end validated
  end given

  given Executable[GotoFirstExercise.Options] with
    extension (options: GotoFirstExercise.Options)
      def execute(): Either[CmtError, String] = {
        val config = new CMTcConfig(options.shared.studentifiedRepo.value)
        GotoExercise
          .Options(exercise = ExerciseId(config.exercises.head.toInt), force = options.force, shared = options.shared)
          .execute()
      }

  val command = new CmtCommand[GotoFirstExercise.Options] {

    def run(options: GotoFirstExercise.Options, args: RemainingArgs): Unit =
      options.validated().flatMap(_.execute()).printResult()
  }

end GotoFirstExercise
