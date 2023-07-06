package com.lunatech.cmt

/** Copyright 2022 - Eric Loots - eric.loots@gmail.com / Trevor Burton-McCreadie - trevor@thinkmorestupidless.com
  *
  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
  * the License. You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  *
  * See the License for the specific language governing permissions and limitations under the License.
  */

import com.lunatech.cmt.TestHelpers.getExercisePrefixAndExercises
import com.lunatech.cmt.admin.Domain.{MainRepository, RenumberOffset, RenumberStart, RenumberStep}
import com.lunatech.cmt.admin.cli.SharedOptions
import com.lunatech.cmt.admin.command.RenumberExercises
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.*
import sbt.io.IO as sbtio
import sbt.io.syntax.*
import Helpers.{commitToGit, initializeGitRepo, setGitConfig}

import java.util.UUID

trait RenumberExercisesFixture:
  val testConfig: String =
    """cmt {
      |  main-repo-exercise-folder = code
      |  studentified-repo-solutions-folder = .cue
      |  studentified-saved-states-folder = .savedStates
      |  studentified-repo-active-exercise-folder = code
      |  linearized-repo-active-exercise-folder = code
      |  config-file-default-name = course-management.conf
      |  test-code-folders = [ "src/test" ]
      |  read-me-files = [ "README.md" ]
      |  cmt-studentified-config-file = .cmt-config
      |  cmt-studentified-dont-touch = [ ".idea", ".bsp", ".bloop" ]
      |}""".stripMargin

  val tempDirectory: File = sbtio.createTemporaryDirectory

  def getMainRepoAndConfig(): (File, File, CMTaConfig) =
    val mainRepo: File = tempDirectory / UUID.randomUUID().toString

    sbtio.createDirectory(mainRepo)
    Helpers.dumpStringToFile(testConfig, mainRepo / "course-management.conf")

    val config: CMTaConfig = CMTaConfig(mainRepo, None)

    val codeFolder = mainRepo / "code"
    sbtio.createDirectory(codeFolder)

    (mainRepo, codeFolder, config)

  def createExercises(codeFolder: File, exercises: Vector[String]): Vector[String] =
    sbtio.createDirectories(exercises.map(exercise => codeFolder / exercise))
    sbtio.touch(exercises.map(exercise => codeFolder / exercise / "README.md"))
    exercises

end RenumberExercisesFixture

final class RenumberExercisesSpec
    extends AnyWordSpec
    with should.Matchers
    with BeforeAndAfterAll
    with RenumberExercisesFixture:

  override def afterAll(): Unit =
    val _ = tempDirectory.delete()

  "RenumberExercises" when {
    "given a renumbering" should {
      val (mainRepo, codeFolder, config) = getMainRepoAndConfig()

      val shared: SharedOptions = SharedOptions(MainRepository(mainRepo), maybeConfigFile = None)

      val exerciseNames =
        Vector("exercise_001_desc", "exercise_002_desc", "exercise_003_desc", "exercise_004_desc", "exercise_005_desc")

      createExercises(codeFolder, exerciseNames)
      initializeGitRepo(mainRepo)
      setGitConfig(mainRepo)
      commitToGit("Initial commit", mainRepo)

      "succeed if exercises are moved to a new offset and renumber step values" in {
        val command =
          RenumberExercises.Options(from = None, to = RenumberOffset(20), step = RenumberStep(2), shared = shared)
        val result = command.execute()
        result shouldBe Right(RenumberExercises.successMessage(command))

        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_020_desc",
          "exercise_022_desc",
          "exercise_024_desc",
          "exercise_026_desc",
          "exercise_028_desc")
        renumberedExercises shouldBe expectedExercises
      }
      "succeed and return the original exercise set when using the default offset and renumber step alues" in {
        val command =
          RenumberExercises.Options(from = None, to = RenumberOffset(1), step = RenumberStep(1), shared = shared)
        val result = command.execute()
        result shouldBe Right(RenumberExercises.successMessage(command))

        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_001_desc",
          "exercise_002_desc",
          "exercise_003_desc",
          "exercise_004_desc",
          "exercise_005_desc")
        renumberedExercises shouldBe expectedExercises
      }
      "succeed if exercises are moved to offset 0 and the first exercise to renumber is the first one in the exercise series" in {
        val command = RenumberExercises.Options(
          from = Some(RenumberStart(1)),
          to = RenumberOffset(0),
          step = RenumberStep(1),
          shared = shared)
        val result = command.execute()
        result shouldBe Right(RenumberExercises.successMessage(command))

        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_000_desc",
          "exercise_001_desc",
          "exercise_002_desc",
          "exercise_003_desc",
          "exercise_004_desc")
        renumberedExercises shouldBe expectedExercises
      }
      "succeed and leave the first exercise number unchanged and create a gap between the first and second exercise" in {
        val command = RenumberExercises.Options(
          from = Some(RenumberStart(1)),
          to = RenumberOffset(10),
          step = RenumberStep(1),
          shared = shared)
        val result = command.execute()
        result shouldBe Right(RenumberExercises.successMessage(command))

        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_000_desc",
          "exercise_010_desc",
          "exercise_011_desc",
          "exercise_012_desc",
          "exercise_013_desc")
        renumberedExercises shouldBe expectedExercises
      }
      "succeed when renumbering moves exercises to the end of the available exercise number space" in {
        val command =
          RenumberExercises.Options(from = None, to = RenumberOffset(995), step = RenumberStep(1), shared = shared)
        val result = command.execute()
        result shouldBe Right(RenumberExercises.successMessage(command))

        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_995_desc",
          "exercise_996_desc",
          "exercise_997_desc",
          "exercise_998_desc",
          "exercise_999_desc")
        renumberedExercises shouldBe expectedExercises
      }
      "fail when renumbering would move outside the available exercise number space and leave the exercise name unchanged" in {
        val command =
          RenumberExercises.Options(from = None, to = RenumberOffset(996), step = RenumberStep(1), shared = shared)
        val result = command.execute()

        result shouldBe Left(
          "Cannot renumber exercises as it would exceed the available exercise number space".toExecuteCommandErrorMessage)
        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_995_desc",
          "exercise_996_desc",
          "exercise_997_desc",
          "exercise_998_desc",
          "exercise_999_desc")
        renumberedExercises shouldBe expectedExercises
      }
      "succeed when moving exercises up to a range that overlaps with the current one" in {
        val command =
          RenumberExercises.Options(from = None, to = RenumberOffset(992), step = RenumberStep(1), shared = shared)
        val result = command.execute()

        result shouldBe Right(RenumberExercises.successMessage(command))
        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_992_desc",
          "exercise_993_desc",
          "exercise_994_desc",
          "exercise_995_desc",
          "exercise_996_desc")
        renumberedExercises shouldBe expectedExercises
      }
      "succeed when moving exercises down to a range that overlaps with the current one" in {
        val command =
          RenumberExercises.Options(from = None, to = RenumberOffset(995), step = RenumberStep(1), shared = shared)
        val result = command.execute()

        result shouldBe Right(RenumberExercises.successMessage(command))
        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_995_desc",
          "exercise_996_desc",
          "exercise_997_desc",
          "exercise_998_desc",
          "exercise_999_desc")
        renumberedExercises shouldBe expectedExercises
      }
      "succeed and return a series of exercises numbered starting at 1 when renumbering with default args" in {
        val command =
          RenumberExercises.Options(from = None, to = RenumberOffset(1), step = RenumberStep(1), shared = shared)
        val result = command.execute()

        result shouldBe Right(RenumberExercises.successMessage(command))
        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_001_desc",
          "exercise_002_desc",
          "exercise_003_desc",
          "exercise_004_desc",
          "exercise_005_desc")
        renumberedExercises shouldBe expectedExercises
      }
      "fail and leave the exercise numbers unchanged when default renumbering is applied again" in {
        val command =
          RenumberExercises.Options(from = None, to = RenumberOffset(1), step = RenumberStep(1), shared = shared)
        val result = command.execute()

        result shouldBe Left("Renumber: nothing to renumber".toExecuteCommandErrorMessage)
        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_001_desc",
          "exercise_002_desc",
          "exercise_003_desc",
          "exercise_004_desc",
          "exercise_005_desc")
        renumberedExercises shouldBe expectedExercises
      }
      "fail when trying to renumber a range of exercises that would clash with other exercises" in {
        val command = RenumberExercises.Options(
          from = Some(RenumberStart(3)),
          to = RenumberOffset(2),
          step = RenumberStep(1),
          shared = shared)
        val result = command.execute()

        result shouldBe Left("Moved exercise range overlaps with other exercises".toExecuteCommandErrorMessage)
        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_001_desc",
          "exercise_002_desc",
          "exercise_003_desc",
          "exercise_004_desc",
          "exercise_005_desc")
        renumberedExercises shouldBe expectedExercises
      }
      "fail when trying to renumber an exercise if it would move into a gap in the numbering" in {
        RenumberExercises
          .Options(from = Some(RenumberStart(3)), to = RenumberOffset(10), step = RenumberStep(1), shared = shared)
          .execute()

        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        val expectedExercises = Vector(
          "exercise_001_desc",
          "exercise_002_desc",
          "exercise_010_desc",
          "exercise_011_desc",
          "exercise_012_desc")
        val result = RenumberExercises
          .Options(from = Some(RenumberStart(12)), to = RenumberOffset(5), step = RenumberStep(1), shared = shared)
          .execute()
        result shouldBe Left("Moved exercise range overlaps with other exercises".toExecuteCommandErrorMessage)
        renumberedExercises shouldBe expectedExercises
      }
    }
    "given any renumbering in a fully packed exercise numbering space" should {
      val (mainRepo, codeFolder, config) = getMainRepoAndConfig()
      val shared: SharedOptions = SharedOptions(MainRepository(mainRepo), maybeConfigFile = None)

      val exerciseNames = Vector.from(0 to 999).map(i => f"exercise_$i%03d_desc")
      val exercises = createExercises(codeFolder, exerciseNames)
      initializeGitRepo(mainRepo)
      setGitConfig(mainRepo)
      commitToGit("Initial commit", mainRepo)

      "fail when trying to shift all exercises one position downwards and leave the exercise name unchanged" in {
        val result = RenumberExercises
          .Options(from = None, to = RenumberOffset(1), step = RenumberStep(1), shared = shared)
          .execute()
        result shouldBe Left(
          "Cannot renumber exercises as it would exceed the available exercise number space".toExecuteCommandErrorMessage)
        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        renumberedExercises shouldBe exercises
      }
      "fail when trying to move the second to last exercise on position downwards and leave the exercise name unchanged" in {
        val result = RenumberExercises
          .Options(from = Some(RenumberStart(998)), to = RenumberOffset(999), step = RenumberStep(1), shared = shared)
          .execute()
        result shouldBe Left(
          "Cannot renumber exercises as it would exceed the available exercise number space".toExecuteCommandErrorMessage)
        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        renumberedExercises shouldBe exercises
      }
      "fail when trying to insert holes in the numbering and leave the exercise name unchanged" in {
        val result = RenumberExercises
          .Options(from = None, to = RenumberOffset(0), step = RenumberStep(2), shared = shared)
          .execute()
        result shouldBe Left(
          "Cannot renumber exercises as it would exceed the available exercise number space".toExecuteCommandErrorMessage)
        val renumberedExercises = getExercisePrefixAndExercises(mainRepo)(config).exercises
        renumberedExercises shouldBe exercises
      }
    }
  }

end RenumberExercisesSpec
