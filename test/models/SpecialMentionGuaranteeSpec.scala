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

import models.ParseError.{AdditionalInfoInvalidCharacters, AdditionalInfoTooLong, AmountStringInvalid, CurrencyCodeInvalid}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar

class SpecialMentionGuaranteeSpec extends AnyFreeSpec with MockitoSugar with Matchers{

  "toDetails" - {
    "returns AdditionalInfoTooLong if is guaranteeReference is longer than 18 characters" in {
      SpecialMentionGuarantee("testPrefixCODFarTooLong")
        .toDetails("FarTooLong") mustBe a[Left[AdditionalInfoTooLong, _]]
    }

    "returns AdditionalInfoInvalidCharacters if characters that aren't alphanumerical or a '.'" in {
      SpecialMentionGuarantee("abcdeabcde$$$$test")
        .toDetails("test") mustBe a[Left[AdditionalInfoInvalidCharacters, _]]
    }

    "returns CurrencyCodeInvalid when currency code is not a 3 letter alphabetical string" in {
      SpecialMentionGuarantee("100.00ABtest")
        .toDetails("test") mustBe a[Left[CurrencyCodeInvalid, _]]
    }

    "returns AmountStringInvalid when amount is not to two decimal places" in {
      SpecialMentionGuarantee("100.0EURtest")
        .toDetails("test") mustBe a[Left[AmountStringInvalid,_]]
    }

    "returns SpecialMentionGuaranteeDetails with all details" in {
      val result = SpecialMentionGuarantee("100.00EURtest")
        .toDetails("test")
      result mustBe a[Right[_,SpecialMentionGuaranteeDetails]]
      result.right.get.currencyCode mustBe Some("EUR")
      result.right.get.guaranteeAmount mustBe Some(BigDecimal(100.00))
    }
    "returns SpecialMentionGuaranteeDetails with no currency code when there is none" in {
      val result = SpecialMentionGuarantee("100.00test")
        .toDetails("test")
      result mustBe a[Right[_,SpecialMentionGuaranteeDetails]]
      result.right.get.currencyCode mustBe None
      result.right.get.guaranteeAmount mustBe Some(BigDecimal(100.00))

    }
    "returns SpecialMentionGuaranteeDetails with no amount when there is none" in {
      val result = SpecialMentionGuarantee("EURtest")
        .toDetails("test")
      result mustBe a[Right[_,SpecialMentionGuaranteeDetails]]
      result.right.get.currencyCode mustBe Some("EUR")
      result.right.get.guaranteeAmount mustBe None

    }
    "returns SpecialMentionGuaranteeDetails with no amount or currency code if there are none" in {
      val result = SpecialMentionGuarantee("test")
        .toDetails("test")
      result mustBe a[Right[_,SpecialMentionGuaranteeDetails]]
      result.right.get.currencyCode mustBe None
      result.right.get.guaranteeAmount mustBe None

    }
  }

}
