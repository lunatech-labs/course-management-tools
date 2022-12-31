package cmt

import cats.data.NonEmptyList

sealed trait CmtError {
  def toDisplayString: String
}

final case class RequiredOptionIsMissing(option: OptionName) extends CmtError {
  override def toDisplayString: String =
    s"${toConsoleRed("ERROR -")} ${toConsoleCyan(s"Missing required option ${option.value}")}"
}
final case class FailedToValidateArgument(option: OptionName, reasons: Seq[ErrorMessage]) extends CmtError {
  override def toDisplayString: String =
    s"${toConsoleRed(s"ERROR - ${toConsoleCyan(s"Option ${option.value}")}:")} ${toConsoleYellow(
        reasons.map(_.message).mkString("\n    ", "\n    ", "\n"))}"
}
object FailedToValidateArgument:
  def because(option: String, messages: String*): FailedToValidateArgument =
    FailedToValidateArgument(OptionName(option), messages.map(ErrorMessage(_)))

final case class FailedToValidateCommandOptions(reasons: List[ErrorMessage]) extends CmtError {
  override def toDisplayString: String =
    s"Failed to validate command options:${reasons.foreach(str => s"\n    $str")}"
}

final case class FailedToExecuteCommand(reason: ErrorMessage) extends CmtError {
  override def toDisplayString: String =
    s"""${toConsoleRed("ERROR -")} ${toConsoleCyan("Failed to execute command.")}
       |  ${toConsoleYellow(reason.message)}""".stripMargin
}

case class OptionName(value: String)
case class ErrorMessage(message: String)

extension (errorMessage: String)
  def toExecuteCommandErrorMessage: FailedToExecuteCommand =
    FailedToExecuteCommand(ErrorMessage(errorMessage))

extension (self: caseapp.core.Error)
  def toCmtError: Set[CmtError] = {
    def extractOther(error: caseapp.core.Error): Set[CmtError] =
      error match {
        case caseapp.core.Error.RequiredOptionNotSpecified(head, tail) =>
          Set(RequiredOptionIsMissing(OptionName(s"""$head, ${tail.mkString(",")}""")))
        case caseapp.core.Error.ParsingArgument(name, e, _) =>
          Set(FailedToValidateArgument(OptionName(name.name), extractErrorMessages(e)))
        case caseapp.core.Error.SeveralErrors(head, tail) => extractOther(head) ++ tail.flatMap(extractOther(_))
        case _                                            => Set.empty
      }

    def extractErrorMessages(error: caseapp.core.Error): List[ErrorMessage] =
      error match {
        case caseapp.core.Error.SeveralErrors(head, tail) =>
          extractErrorMessages(head) ++ tail.flatMap(extractErrorMessages(_))
        case caseapp.core.Error.Other(message) => List(ErrorMessage(message))
        case _                                 => List.empty
      }

    extractOther(self)
  }
