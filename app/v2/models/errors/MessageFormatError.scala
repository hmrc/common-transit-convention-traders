package v2.models.errors

sealed trait MessageFormatError

object MessageFormatError {
  case class UnexpectedError(thr: Option[Throwable] = None) extends MessageFormatError
}
