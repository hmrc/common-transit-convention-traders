/*
 * Copyright 2021 HM Revenue & Customs
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

import models.ParseError.AdditionalInfoInvalidCharacters
import models.ParseError.AmountStringInvalid
import models.ParseError.AmountStringTooLong
import models.ParseError.CurrencyCodeInvalid
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar

class SpecialMentionGuaranteeSpec extends AnyFreeSpec with MockitoSugar with Matchers {

  "toDetails" - {
    "returns AdditionalInfoInvalidCharacters if characters that aren't alphanumerical or a '.'" in {
      SpecialMentionGuarantee("abcdeabcde$$$$test", Nil)
        .toDetails("test") mustBe a[Left[AdditionalInfoInvalidCharacters, _]]
    }

    "returns CurrencyCodeInvalid when currency code is not a 3 letter alphabetical string" in {
      SpecialMentionGuarantee("100.00ABtest", Nil)
        .toDetails("test") mustBe a[Left[CurrencyCodeInvalid, _]]
    }

    "returns AmountStringTooLong if is amount value is longer than 18 characters" in {
      SpecialMentionGuarantee("12345678901234567890.00EURtest", Nil)
        .toDetails("test") mustBe a[Left[AmountStringTooLong, _]]
    }

    "returns AmountStringInvalid if amount value cannot be parsed as a big decimal" in {
      SpecialMentionGuarantee("ABCEURtest", Nil)
        .toDetails("test") mustBe a[Left[AmountStringInvalid, _]]
    }

    "returns SpecialMentionGuaranteeDetails with all details" in {
      val result = SpecialMentionGuarantee("100.00EURtest", Nil)
        .toDetails("test")
      result mustBe a[Right[_, SpecialMentionGuaranteeDetails]]
      result.right.get.currencyCode mustBe Some("EUR")
      result.right.get.guaranteeAmount mustBe Some(BigDecimal(100.00))
    }
  }

}
