package cmt.admin.command

import caseapp.{Command, CommandName, HelpMessage, Recurse, RemainingArgs}
import cmt.{CMTaConfig, CmtError, printResult}
import cmt.Helpers.{ExercisesMetadata, extractExerciseNr, getExerciseMetadata, validatePrefixes}
import cmt.admin.Domain.{RenumberOffset, RenumberStart, RenumberStep}
import cmt.admin.cli.SharedOptions
import cmt.core.execution.Executable
import cmt.admin.cli.ArgParsers.{renumberOffsetArgParser, renumberStartArgParser, renumberStepArgParser}
import cmt.admin.command.RenumberExercises
import cmt.core.validation.Validatable
import sbt.io.IO as sbtio
import sbt.io.syntax.*
import cmt.toExecuteCommandErrorMessage

object RenumberExercises:

  def successMessage(options: Options): String =
    s"Renumbered exercises in ${options.shared.mainRepository.value.getPath} from ${options.maybeStart} to ${options.offset.value} by ${options.step.value}"

  @CommandName("renumber-exercises")
  @HelpMessage("renumbers the exercises in the main repository")
  final case class Options(
      maybeStart: Option[RenumberStart],
      offset: RenumberOffset,
      step: RenumberStep,
      @Recurse shared: SharedOptions)

  given Validatable[RenumberExercises.Options] with
    extension (options: RenumberExercises.Options)
      def validated(): Either[CmtError, RenumberExercises.Options] =
        Right(options)
  end given

  given Executable[RenumberExercises.Options] with
    extension (options: RenumberExercises.Options)
      def execute(): Either[CmtError, String] = {
        import RenumberExercisesHelpers.*

        val mainRepository = options.shared.mainRepository
        val config = new CMTaConfig(mainRepository.value, options.shared.maybeConfigFile.map(_.value))

        for {
          ExercisesMetadata(exercisePrefix, exercises, exerciseNumbers) <- getExerciseMetadata(mainRepository.value)(
            config)

          mainRepoExerciseFolder = mainRepository.value / config.mainRepoExerciseFolder

          renumStartAt <- resolveStartAt(options.maybeStart.map(_.value), exerciseNumbers)

          splitIndex = exerciseNumbers.indexOf(renumStartAt)
          (exerciseNumsBeforeSplit, exerciseNumsAfterSplit) = exerciseNumbers.splitAt(splitIndex)
          (_, exercisesAfterSplit) = exercises.splitAt(splitIndex)

          renumOffset = options.offset.value
          tryMove = (exerciseNumsBeforeSplit, exerciseNumsAfterSplit) match
            case (Vector(), Vector(`renumOffset`, _)) =>
              Left("Renumber: nothing to renumber".toExecuteCommandErrorMessage)
            case (before, _) if rangeOverlapsWithOtherExercises(before, renumOffset) =>
              Left("Moved exercise range overlaps with other exercises".toExecuteCommandErrorMessage)
            case (_, _)
                if exceedsAvailableSpace(
                  exercisesAfterSplit,
                  renumOffset = renumOffset,
                  renumStep = options.step.value) =>
              Left(
                s"Cannot renumber exercises as it would exceed the available exercise number space".toExecuteCommandErrorMessage)
            case (_, _) =>
              val moves =
                for {
                  (exercise, index) <- exercisesAfterSplit.zipWithIndex
                  newNumber = renumOffset + index * options.step.value
                  oldExerciseFolder = mainRepoExerciseFolder / exercise
                  newExerciseFolder =
                    mainRepoExerciseFolder / renumberExercise(exercise, exercisePrefix, newNumber)
                  if oldExerciseFolder != newExerciseFolder
                } yield (oldExerciseFolder, newExerciseFolder)

              if moves.isEmpty
              then Left("Renumber: nothing to renumber".toExecuteCommandErrorMessage)
              else
                if renumOffset > renumStartAt
                then sbtio.move(moves.reverse)
                else sbtio.move(moves)
                Right(
                  s"Renumbered exercises in ${mainRepository.value.getPath} from ${options.maybeStart} to ${options.offset.value} by ${options.step.value}")

          moveResult <- tryMove
        } yield moveResult
      }
  end given

  private object RenumberExercisesHelpers:
    def resolveStartAt(renumStartAtOpt: Option[Int], exerciseNumbers: Vector[Int]): Either[CmtError, Int] = {
      renumStartAtOpt match
        case None => Right(exerciseNumbers.head)
        case Some(num) =>
          if exerciseNumbers.contains(num)
          then Right(num)
          else Left(s"No exercise with number $num".toExecuteCommandErrorMessage)
    }
    end resolveStartAt

    def exceedsAvailableSpace(exercisesAfterSplit: Vector[String], renumOffset: Int, renumStep: Int): Boolean =
      renumOffset + (exercisesAfterSplit.size - 1) * renumStep > 999
    end exceedsAvailableSpace

    def rangeOverlapsWithOtherExercises(before: Vector[Int], renumOffset: Int): Boolean =
      before.nonEmpty && (renumOffset <= before.last)
    end rangeOverlapsWithOtherExercises
  end RenumberExercisesHelpers

  val command = new Command[RenumberExercises.Options] {
    def run(options: RenumberExercises.Options, args: RemainingArgs): Unit =
      options.validated().flatMap(_.execute()).printResult()
  }

end RenumberExercises
