package models

trait ParseError {
  def message: String
}

object ParseError {
  case class EmptyNodeSeq(message: String)                    extends ParseError
  case class NoGuaranteeType(message: String)                 extends ParseError
  case class GuaranteeTypeInvalid(message: String)            extends ParseError
  case class NoGuaranteeReferenceNumber(message: String)      extends ParseError
  case class GuaranteeAmountZero(message: String)             extends ParseError
  case class AdditionalInfoMissing(message: String)           extends ParseError
  case class AdditionalInfoTooLong(message: String)           extends ParseError
  case class AdditionalInfoInvalidCharacters(message: String) extends ParseError
  case class CurrencyCodeInvalid(message: String)             extends ParseError
  case class AmountStringInvalid(message: String)             extends ParseError
  case class SpecialMentionNotFound(message: String)          extends ParseError
}
