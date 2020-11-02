/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

trait ParseError {
  def message: String
}

object ParseError extends ParseHandling {
  case class EmptyNodeSeq(message: String)                    extends ParseError
  case class NoGuaranteeType(message: String)                 extends ParseError
  case class GuaranteeTypeInvalid(message: String)            extends ParseError
  case class NoGuaranteeReferenceNumber(message: String)      extends ParseError
  case class GuaranteeAmountZero(message: String)             extends ParseError
  case class AdditionalInfoMissing(message: String)           extends ParseError
  case class AdditionalInfoTooLong(message: String)           extends ParseError
  case class AdditionalInfoInvalidCharacters(message: String) extends ParseError
  case class AdditionalInfoCodeMissing(message: String)       extends ParseError
  case class CurrencyCodeInvalid(message: String)             extends ParseError
  case class AmountStringInvalid(message: String)             extends ParseError
  case class SpecialMentionNotFound(message: String)          extends ParseError
  case class GuaranteeNotFound(message: String)               extends ParseError
  case class MissingItemNumber(message: String)               extends ParseError
  case class InvalidItemNumber(message: String)               extends ParseError

  def sequenceErrors[A](input: Seq[ParseHandler[A]]): ParseHandler[Seq[A]] =
    input.filterNot(i => i.isRight).headOption match {
      case Some(error) => Left(error.left.get)
      case None => Right(input.map {
        x => x.right.get
      })
    }
}
