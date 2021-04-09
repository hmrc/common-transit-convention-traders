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

package utils.guaranteeParsing

import models.{ChangeGuaranteeInstruction, NoChangeGuaranteeInstruction, NoChangeInstruction, SpecialMentionGuarantee}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.guice.GuiceApplicationBuilder

class XmlBuilderSpec extends AnyFreeSpec with Matchers {

  protected def baseApplicationBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm" -> false
      )

  def sut: XmlBuilder = {
    val application = baseApplicationBuilder
      .build()

    application.injector.instanceOf[XmlBuilder]
  }

  "buildFromInstruction" - {
    "returns NodeSeq based on the inputted instruction" in {
      val resultNoChange = sut.buildFromInstruction(NoChangeInstruction(<example></example>)).toString()
      resultNoChange.toString().filter(_ > ' ') mustBe
        "<example></example>".filter(_ > ' ')

      val resultNoChangeGuarantee = sut.buildFromInstruction(NoChangeGuaranteeInstruction(SpecialMentionGuarantee("testInfo")))
      resultNoChangeGuarantee.toString().filter(_ > ' ') mustBe
        "<SPEMENMT2><AddInfMT21>testInfo</AddInfMT21><AddInfCodMT23>CAL</AddInfCodMT23></SPEMENMT2>".filter(_ > ' ')


      val resultChangeGuarantee = sut.buildFromInstruction(ChangeGuaranteeInstruction(SpecialMentionGuarantee("10000.00EURtestCode")))
      resultChangeGuarantee.toString().filter(_ > ' ') mustBe
        "<SPEMENMT2><AddInfMT21>10000.00EURtestCode</AddInfMT21><AddInfCodMT23>CAL</AddInfCodMT23></SPEMENMT2>".filter(_ > ' ')

    }
  }

  "buildGuaranteeXml" - {
    "returns xml with additionalInfo and CAL" in {
      sut.buildGuaranteeXml(SpecialMentionGuarantee("testInfo")) mustBe
        <SPEMENMT2><AddInfMT21>testInfo</AddInfMT21><AddInfCodMT23>CAL</AddInfCodMT23></SPEMENMT2>
    }
  }
}
