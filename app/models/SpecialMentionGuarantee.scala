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

import models.ParseError.{AdditionalInfoInvalidCharacters, AmountStringInvalid, AmountStringTooLong, CurrencyCodeInvalid}

import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

sealed trait SpecialMention

final case class SpecialMentionOther(xml: NodeSeq) extends SpecialMention

final case class SpecialMentionGuarantee(additionalInfo: String) extends SpecialMention {
  def toDetails(guaranteeReference: String) : Either[ParseError, SpecialMentionGuaranteeDetails] = {
    if(additionalInfo.matches("'[a-zA-Z0-9_.]*'")) return Left(AdditionalInfoInvalidCharacters("invalid characters in additional info"))

    getCurrencyCode(additionalInfo, guaranteeReference) match {
      case Left(error) => Left(error)
      case Right(currencyOpt) => {
        getAmount(additionalInfo, guaranteeReference, currencyOpt) match {
          case Left(error) => Left(error)
          case Right(amountOpt) => Right(SpecialMentionGuaranteeDetails(amountOpt, currencyOpt, guaranteeReference))
        }
      }
    }
  }

  private def getCurrencyCode(additionalInfo: String, guaranteeReference: String): Either[ParseError, Option[String]] = {
    if(additionalInfo.length >= (guaranteeReference.length + 3))
    {
      val currencyStart = additionalInfo.length - guaranteeReference.length - 3
      val currencyEnd = currencyStart + 3
      val currencyCode = additionalInfo.substring(currencyStart, currencyEnd)
      if(currencyCode.matches("[A-Z]{3}"))
      Right(Some(currencyCode))
      else if(additionalInfo.head.isDigit)
      {
        Right(None)
      }
      else Left(CurrencyCodeInvalid(s"Invalid Currency Code: $currencyCode"))
    }
    else Right(None)
  }

  private def getAmount(additionalInfo: String, guaranteeReference: String, currencyOpt: Option[String]): Either[ParseError, Option[BigDecimal]] = {
    val cutIndex = currencyOpt match {
      case None => additionalInfo.length - guaranteeReference.length
      case Some(code) => additionalInfo.length - (code.length + guaranteeReference.length)
    }
    val amountString = additionalInfo.substring(0, cutIndex)
    if(amountString.isEmpty)
    {
      Right(None)
    }
    else if(amountString.length >= 16) {
      Left(AmountStringTooLong("Guarantee amount should be not be longer than 15 characters"))
    }
    else {
      Try(BigDecimal(amountString)) match {
        case Success(bigDecimal) => Right(Some(bigDecimal))
        case Failure(_) =>  Left(AmountStringInvalid("Amount String Invalid"))
      }
      }
  }
}
