package v2.models.errors

sealed trait ConversionError

object ConversionError {
  case class UnexpectedError(thr: Option[Throwable] = None) extends ConversionError
}
