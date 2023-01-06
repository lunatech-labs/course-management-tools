package cmt.client

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

import cmt.{CmtError, FailedToValidateArgument, OptionName}
import sbt.io.syntax.{File, file}

import scala.util.{Success, Try}

object Domain:
  final case class ExerciseId(value: String)
  object ExerciseId:
    val default: ExerciseId = ExerciseId("")

  final case class StudentifiedRepo(value: File)
  final case class ForceMoveToExercise(forceMove: Boolean)
  object StudentifiedRepo:
    val default: StudentifiedRepo = StudentifiedRepo(file(".").getAbsoluteFile.getParentFile)

  final case class TemplatePath(value: String)
  object TemplatePath:
    val default: TemplatePath = TemplatePath("")

  abstract case class GithubCourseRef private (organisation: String, project: String) {
    def asString(): String = s"$organisation/$project"
  }
  object GithubCourseRef {
    def fromString(value: String): Either[CmtError, GithubCourseRef] = {
      Try {
        val (organisation, project) = {
          val split = value.split("/")
          (split(0), split(1))
        }
      } {
        case Success((organisation, project)) => Right(new GithubCourseRef(organisation, project) {})
        case Failure(error)                   => Left(FailedToValidateArgument.because("course", error.tost))
      }

    }
  }
