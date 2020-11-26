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

import cats.implicits._
import cats.data._
import cats._


sealed trait ParseError {
  def message: String
}

object ParseError extends ParseHandling {
  final case class EmptyNodeSeq(message: String)                    extends ParseError
  final case class NoGuaranteeType(message: String)                 extends ParseError
  final case class GuaranteeTypeInvalid(message: String)            extends ParseError
  final case class NoGuaranteeReferenceNumber(message: String)      extends ParseError
  final case class NoOtherGuaranteeField(message: String)           extends ParseError
  final case class GuaranteeAmountZero(message: String)             extends ParseError
  final case class AdditionalInfoMissing(message: String)           extends ParseError
  final case class AdditionalInfoInvalidCharacters(message: String) extends ParseError
  final case class AdditionalInfoCodeMissing(message: String)       extends ParseError
  final case class CurrencyCodeInvalid(message: String)             extends ParseError
  final case class AmountStringTooLong(message: String)             extends ParseError
  final case class AmountStringInvalid(message: String)             extends ParseError
  final case class SpecialMentionNotFound(message: String)          extends ParseError
  final case class GuaranteeNotFound(message: String)               extends ParseError
  final case class MissingItemNumber(message: String)               extends ParseError
  final case class InvalidItemNumber(message: String)               extends ParseError
  final case class AmountWithoutCurrency(message: String)           extends ParseError

  def sequenceErrors[A](input: Seq[ParseHandler[A]]): ParseHandler[Seq[A]] = {
    input.toList.sequence.map { _.toSeq }
  }
}
