package cmt.support

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

import cmt.Helpers.*
import cmt.admin.Domain.MainRepository
import cmt.admin.cli.SharedOptions
import cmt.admin.command
import cmt.client.Domain.{ForceMoveToExercise, StudentifiedRepo}
import cmt.{CMTaConfig, CMTcConfig, CmtError, Helpers}
import sbt.io.IO as sbtio
import sbt.io.syntax.*

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.annotation.targetName

type FilePath = String
type ExerciseName = String
type SourceFileStruct = List[FilePath]
final case class SourcesStruct(test: SourceFileStruct, readme: SourceFileStruct, main: SourceFileStruct)
opaque type ExerciseMetadata = Map[ExerciseName, SourcesStruct]

extension (sfs: SourceFileStruct)
  def toSourceFiles: SourceFiles =
    sfs.map(n => (n, UUID.randomUUID())).to(Map).map { case (k, v) => (adaptToOSSeparatorChar(k), v) }

object ExerciseMetadata:
  def apply(): ExerciseMetadata = Map.empty

  extension (es: ExerciseMetadata)
    def addExercise(exercise: (ExerciseName, SourcesStruct)): ExerciseMetadata = es + exercise

    def toExercises: Exercises = es.view
      .mapValues { case SourcesStruct(test, readme, main) =>
        ExercisesStruct(test = test.toSourceFiles, readme = readme.toSourceFiles, main = main.toSourceFiles)
      }
      .to(Map)

end ExerciseMetadata

type CheckSum = UUID
opaque type SourceFiles = Map[FilePath, CheckSum]
final case class ExercisesStruct(test: SourceFiles, readme: SourceFiles, main: SourceFiles)
opaque type Exercises = Map[ExerciseName, ExercisesStruct]

object Exercises:
  extension (exercises: Exercises)
    def createRepo(
        baseFolder: File,
        codeFolder: String,
        additionalFiles: Option[SourceFiles] = None): Either[String, Unit] =
      for {
        exercise <- exercises.keys
        ExercisesStruct(test, readme, main) = exercises(exercise)

      } {
        test.createFiles(baseFolder, exercise, codeFolder)
        readme.createFiles(baseFolder, exercise, codeFolder)
        main.createFiles(baseFolder, exercise, codeFolder)
      }
      initializeGitRepo(baseFolder)
      setGitConfig(baseFolder)
      commitToGit("Initial commit", baseFolder)
      Right(())

    def getMainCode(exerciseName: ExerciseName): SourceFiles =
      exercises(exerciseName).main

    def getMainFile(exerciseName: ExerciseName, filePath: String): Tuple2[FilePath, CheckSum] =
      adaptToOSSeparatorChar(filePath) -> getMainCode(exerciseName)(adaptToOSSeparatorChar(filePath))

    def getTestCode(exerciseName: ExerciseName): SourceFiles =
      exercises(exerciseName).test

    def getReadmeCode(exerciseName: ExerciseName): SourceFiles =
      exercises(exerciseName).readme

    def getAllCode(exerciseName: ExerciseName): SourceFiles =
      getMainCode(exerciseName) ++ getTestCode(exerciseName) ++ getReadmeCode(exerciseName)

end Exercises

object SourceFiles:
  def apply(sourceFiles: Map[FilePath, UUID]): SourceFiles = sourceFiles

  extension (sf: SourceFiles)
    def createFiles(baseFolder: File, exercise: String, codeFolder: String): Unit =
      sf.foreach { case (filePath, checksum) =>
        sbtio.touch(baseFolder / codeFolder / exercise / filePath)
        dumpStringToFile(checksum.toString, baseFolder / codeFolder / exercise / filePath)
      }

    def moveFile(baseFolder: File, fromPath: String, toPath: String): SourceFiles =
      for {
        (path, checksum) <- sf
        tp =
          if (adaptToOSSeparatorChar(fromPath) == path)
            val localToPath = adaptToOSSeparatorChar(toPath)
            sbtio.move(baseFolder / path, baseFolder / localToPath)
            localToPath
          else path
      } yield (tp, checksum)

    @targetName("mergeWith")
    def ++(other: SourceFiles): SourceFiles =
      sf ++ other

    @targetName("mergeFile")
    def +(other: Tuple2[FilePath, CheckSum]): SourceFiles =
      sf + other

    @targetName("minus")
    def --(other: SourceFiles): SourceFiles =
      sf -- other.keys

    def doesNotContain(otherSourceFiles: SourceFiles): Unit =
      for {
        (filePath, _) <- otherSourceFiles
      } assert(!sf.contains(filePath), s"Actual sourceFiles shouldn't contain ${filePath}")
end SourceFiles

def createMainRepo(tmpDir: File, repoName: String, exercises: Exercises, testConfig: String): File =
  import Exercises.*
  val mainRepo = tmpDir / repoName
  sbtio.touch(mainRepo / "course-management.conf")
  Helpers.dumpStringToFile(testConfig, mainRepo / "course-management.conf")
  println(s"Mainrepo = $mainRepo")
  exercises.createRepo(mainRepo, "code")
  mainRepo

def studentifyMainRepo(tmpDir: File, repoName: String, mainRepo: File): File =
  import cmt.admin.Domain.{ForceDeleteDestinationDirectory, InitializeGitRepo, StudentifyBaseDirectory}
  import cmt.admin.command.AdminCommand.Studentify

  val studentifyBase = tmpDir / "stu"
  sbtio.createDirectory(studentifyBase)
  val cmd = command.Studentify.Options(
    studentifyBaseDirectory = StudentifyBaseDirectory(studentifyBase),
    forceDelete = ForceDeleteDestinationDirectory(false),
    initGit = InitializeGitRepo(false),
    shared = SharedOptions(mainRepository = MainRepository(mainRepo)))
//  val cmd = Studentify(
//    MainRepository(mainRepo),
//    new CMTaConfig(mainRepo, Some(mainRepo / "course-management.conf")),
//    StudentifyBaseDirectory(studentifyBase),
//    forceDeleteDestinationDirectory = ForceDeleteDestinationDirectory(false),
//    initializeAsGitRepo = InitializeGitRepo(false))
  cmd.execute()
  studentifyBase / repoName

def extractCodeFromRepo(codeFolder: File): SourceFiles =
  val files = Helpers.fileList(codeFolder)
  val filesAndChecksums = for {
    file <- files
    Some(fileName) = file.relativeTo(codeFolder): @unchecked
    checksum = java.util.UUID.fromString(sbtio.readLines(file, StandardCharsets.UTF_8).head)
  } yield (fileName.getPath, checksum)
  SourceFiles(filesAndChecksums.to(Map))

def gotoNextExercise(config: CMTcConfig, studentifiedRepo: File): Either[CmtError, String] =
  import cmt.client.command.ClientCommand.NextExercise
  import cmt.client.command.execution.given
  NextExercise(config, ForceMoveToExercise(false), StudentifiedRepo(studentifiedRepo)).execute()

def gotoNextExerciseForced(config: CMTcConfig, studentifiedRepo: File): Either[CmtError, String] =
  import cmt.client.command.ClientCommand.NextExercise
  import cmt.client.command.execution.given
  NextExercise(config, ForceMoveToExercise(true), StudentifiedRepo(studentifiedRepo)).execute()

def gotoPreviousExercise(config: CMTcConfig, studentifiedRepo: File): Either[CmtError, String] =
  import cmt.client.command.ClientCommand.PreviousExercise
  import cmt.client.command.execution.given
  PreviousExercise(config, ForceMoveToExercise(false), StudentifiedRepo(studentifiedRepo)).execute()

def gotoPreviousExerciseForced(config: CMTcConfig, studentifiedRepo: File): Either[CmtError, String] =
  import cmt.client.command.ClientCommand.PreviousExercise
  import cmt.client.command.execution.given
  PreviousExercise(config, ForceMoveToExercise(true), StudentifiedRepo(studentifiedRepo)).execute()

def pullSolution(config: CMTcConfig, studentifiedRepo: File): Either[CmtError, String] =
  import cmt.client.command.ClientCommand.PullSolution
  import cmt.client.command.execution.given
  PullSolution(config, StudentifiedRepo(studentifiedRepo)).execute()

def gotoExercise(config: CMTcConfig, studentifiedRepo: File, exercise: String): Either[CmtError, String] =
  import cmt.client.Domain.ExerciseId
  import cmt.client.command.ClientCommand.GotoExercise
  import cmt.client.command.execution.given
  GotoExercise(config, ForceMoveToExercise(false), StudentifiedRepo(studentifiedRepo), ExerciseId(exercise)).execute()

def gotoFirstExercise(config: CMTcConfig, studentifiedRepo: File): Either[CmtError, String] =
  import cmt.client.command.ClientCommand.GotoFirstExercise
  import cmt.client.command.execution.given
  GotoFirstExercise(config, ForceMoveToExercise(false), StudentifiedRepo(studentifiedRepo)).execute()

def saveState(config: CMTcConfig, studentifiedRepo: File): Either[CmtError, String] =
  import cmt.client.command.ClientCommand.SaveState
  import cmt.client.command.execution.given
  SaveState(config, StudentifiedRepo(studentifiedRepo)).execute()

def restoreState(config: CMTcConfig, studentifiedRepo: File, exercise: String): Either[CmtError, String] =
  import cmt.client.Domain.ExerciseId
  import cmt.client.command.ClientCommand.RestoreState
  import cmt.client.command.execution.given
  RestoreState(config, StudentifiedRepo(studentifiedRepo), ExerciseId(exercise)).execute()

def pullTemplate(config: CMTcConfig, studentifiedRepo: File, templatePath: String): Either[CmtError, String] =
  import cmt.client.Domain.TemplatePath
  import cmt.client.command.ClientCommand.PullTemplate
  import cmt.client.command.execution.given
  PullTemplate(config, StudentifiedRepo(studentifiedRepo), TemplatePath(Helpers.adaptToOSSeparatorChar(templatePath)))
    .execute()

def addFileToStudentifiedRepo(studentifiedRepo: File, filePath: String): SourceFiles =
  val fileContent = UUID.randomUUID()
  sbtio.touch(studentifiedRepo / filePath)
  dumpStringToFile(fileContent.toString, studentifiedRepo / filePath)
  SourceFiles(Map(filePath -> fileContent))
