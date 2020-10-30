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

package utils.guaranteeParsing

import data.TestXml
import models.ParseError.GuaranteeAmountZero
import models.{Guarantee, ParseErrorSpec, SpecialMentionGuarantee, SpecialMentionGuaranteeDetails}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.guice.GuiceApplicationBuilder

class DefaultGuaranteeApplierSpec  extends AnyFreeSpec with MockitoSugar with BeforeAndAfterEach with TestXml with Matchers with ScalaCheckPropertyChecks {

  protected def baseApplicationBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm" -> false
      )

  def sut: DefaultGuaranteeApplier = {
    val application = baseApplicationBuilder
      .build()

    application.injector.instanceOf[DefaultGuaranteeApplier]
  }

  "applyDefaultGuarantee" - {
    "returns an error if SpecialMentionGuarantee cannot convert to details" in {
      val result = sut.applyDefaultGuarantee(Guarantee(1, "alpha"), SpecialMentionGuarantee("100.00EURalphabetacharliedeltaepsilon"))
      result mustBe a[Left[ParseErrorSpec, _]]
    }

    "returns SpecialMentionGuaranteeDetails with overwritten guarantee value if there is no guarantee amount" in {
      val result = sut.applyDefaultGuarantee(Guarantee(1, "alpha"), SpecialMentionGuarantee("alpha"))
      result mustBe a[Right[_, SpecialMentionGuaranteeDetails]]
      result.right.get.guaranteeAmount mustBe Some(BigDecimal(10000.00))
      result.right.get.currencyCode mustBe Some("EUR")
    }

    "returns GuaranteeAmountZero parse error if amount is specified as zero" in {
      val result = sut.applyDefaultGuarantee(Guarantee(1, "alpha"), SpecialMentionGuarantee("0.00EURalpha"))
      result mustBe a[Left[GuaranteeAmountZero, _]]
    }

    "returns SpecialMentionGuarantee as SpecialMentionGuaranteeDetails is no change required" in {
      val result = sut.applyDefaultGuarantee(Guarantee(1, "alpha"), SpecialMentionGuarantee("100.00EURalpha"))
      result mustBe a[Right[_, SpecialMentionGuaranteeDetails]]
      result.right.get.guaranteeAmount mustBe Some(BigDecimal(100.00))
      result.right.get.currencyCode mustBe Some("EUR")
    }
  }

}
