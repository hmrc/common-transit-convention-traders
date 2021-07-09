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

package services

import data.{EnsureGuaranteeServiceTestData => TestData}
import models.Guarantee
import models.request.DepartureDeclarationXSD
import org.scalacheck.{Gen, Shrink}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.xml.NodeSeq

class EnsureGuaranteeServiceIntegrationSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with utils.WiremockSuite with ScalaFutures with IntegrationPatience with ScalaCheckPropertyChecks {

  implicit def noShrink[A]: Shrink[A] = Shrink.shrinkAny[A]

  def service = app.injector.instanceOf[EnsureGuaranteeService]
  def validator = app.injector.instanceOf[XmlValidationService]

  def normalise(nodeSeq: NodeSeq): String = nodeSeq.toString().filter(_ > ' ')

  "ensureGuarantee" - {
    "discovered scenarios" - {
      "must default value correct" in {
        val result = service.ensureGuarantee(TestData.buildGBEUXml(TestData.standardInputXML))

        normalise(result.right.get) mustEqual TestData.buildGBEUXml(TestData.standardExpectedXML).toString().filter(_ > ' ')

      }
      "result must pass standard validation" in {
        val result = service.ensureGuarantee(TestData.buildGBEUXml(TestData.standardInputXML))

        validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
      }
      "result must not be changed if no standard guarantees" in {
        val result = service.ensureGuarantee(TestData.buildGBEUXml(TestData.otherInputXML))

        normalise(result.right.get) mustEqual normalise(TestData.buildGBEUXml(TestData.otherInputXML))
      }
      "must add default special mentions to first good if no special mention present for guarantees" in {
        val result = service.ensureGuarantee(TestData.buildGBEUXml(TestData.extraGuaranteesInputXML))

        normalise(result.right.get) mustEqual normalise(TestData.buildGBEUXml(TestData.extraGuaranteesExpectedXML))
        validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
      }
      "must add default special mentions to first good if special mention isn't present for a guarantee" in {
        val result = service.ensureGuarantee(TestData.buildGBEUXml(TestData.extraGuaranteesComboInputXML))

        normalise(result.right.get) mustEqual normalise(TestData.buildGBEUXml(TestData.extraGuaranteesComboExpectedXML))
        validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
      }
      "must allow non-guarantee special mentions through without issue" in {
        val result = service.ensureGuarantee(TestData.buildGBEUXml(TestData.oddSpecialMentionsInputXml))

        result.right.get.toString().filter(_ > ' ') mustEqual TestData.buildGBEUXml(TestData.oddSpecialMentionsOutputXml).toString().filter(_ > ' ')
        validator.validate(result.right.get.toString().filter(_ > ' '), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]

      }
      "must allow CAL special mentions with additional values through without removing fields" in {
        val result = service.ensureGuarantee(TestData.buildGBEUXml(TestData.mixedSpecialMentionsInputXml))

        normalise(result.right.get) mustEqual normalise(TestData.buildGBEUXml(TestData.mixedSpecialMentionsOutputXml))
        validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
      }
    }
  }

  "listed scenarios" - {

    val addedSpecialMention =
      <SPEMENMT2>
        <AddInfMT21>1.00GBP21GB0000010000HU1</AddInfMT21>
        <AddInfCodMT23>CAL</AddInfCodMT23>
      </SPEMENMT2>

    "GB -> EU, type 1, CAL w/o Reference Number - SM Passes through to core unedited, new one added" in {
      val customSpecialMention =
        <SPEMENMT2>
          <AddInfCodMT23>CAL</AddInfCodMT23>
        </SPEMENMT2>
      val insertXml = TestData.basicGuarantee ++ TestData.goodsWithCustomSpecialMention(customSpecialMention)
      val xml = TestData.buildGBEUXml(insertXml)
      val expectedXml = TestData.buildGBEUXml(TestData.basicGuarantee ++ TestData.goodsWithCustomSpecialMention(customSpecialMention ++ addedSpecialMention))
      val result = service.ensureGuarantee(xml)
      normalise(result.right.get) mustEqual normalise(expectedXml)
      validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
    }

    "GB -> EU, type 1, non-CAL Special Mentions - SM Passes through to core unedited, new one added" in {
      val customSpecialMention =
        <SPEMENMT2>
          <ExpFroECMT24>4</ExpFroECMT24>
          <ExpFroCouMT25>az</ExpFroCouMT25>
        </SPEMENMT2>
      val insertXml = TestData.basicGuarantee ++ TestData.goodsWithCustomSpecialMention(addedSpecialMention ++ customSpecialMention)
      val xml = TestData.buildGBEUXml(insertXml)
      val result = service.ensureGuarantee(xml)
      normalise(result.right.get) mustEqual normalise(xml)
      validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
    }

    "GB -> EU, with Reference Type, Has Reference Number, CAL Special Mentions, Currency & Value > 0.01 - should pass through unedited" in {
      forAll(Gen.oneOf(Guarantee.referenceTypes)) {
        typeNum =>
          val insertXml = TestData.guaranteeWithType(typeNum) ++ TestData.goodsWithCustomSpecialMention(addedSpecialMention)
          val xml = TestData.buildGBEUXml(insertXml)
          val result = service.ensureGuarantee(xml)
          normalise(result.right.get) mustEqual normalise(xml)
          validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
      }
    }

    "GB -> EU, with Reference Type, Has Reference Number, No Special Mentions, Currency & Value > 0.01 - should pass through with a new Special Mention inserted" in {
      forAll(Gen.oneOf(Guarantee.referenceTypes)) {
        typeNum =>
          val insertXml = TestData.guaranteeWithType(typeNum) ++ TestData.goodsWithCustomSpecialMention(Nil)
          val xml = TestData.buildGBEUXml(insertXml)
          val expectedXml = TestData.buildGBEUXml(TestData.guaranteeWithType(typeNum) ++ TestData.goodsWithCustomSpecialMention(addedSpecialMention))
          val result = service.ensureGuarantee(xml)
          normalise(result.right.get) mustEqual normalise(expectedXml)
          validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
      }
    }

    "GB -> EU, with Reference Type, Has Reference Number, Non-CAL Special Mentions - should pass through adding a new Special Mention for the guarantee" in {
      forAll(Gen.oneOf(Guarantee.referenceTypes)) {
        typeNum =>
          val custom =
            <SPEMENMT2>
              <ExpFroECMT24>4</ExpFroECMT24>
              <ExpFroCouMT25>az</ExpFroCouMT25>
            </SPEMENMT2>
          val insertXml = TestData.guaranteeWithType(typeNum) ++ TestData.goodsWithCustomSpecialMention(custom)
          val xml = TestData.buildGBEUXml(insertXml)
          val expectedXml = TestData.buildGBEUXml(TestData.guaranteeWithType(typeNum) ++ TestData.goodsWithCustomSpecialMention(custom ++ addedSpecialMention))
          val result = service.ensureGuarantee(xml)
          normalise(result.right.get) mustEqual normalise(expectedXml)
          validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
      }
    }

    "GB -> GB, with Reference Type, Has Reference Number, CAL Special Mentions, Currency & Value > 0.01 - should pass through unedited" in {
      forAll(Gen.oneOf(Guarantee.referenceTypes)) {
        typeNum =>
          val insertXml = TestData.guaranteeWithType(typeNum) ++ TestData.goodsWithCustomSpecialMention(addedSpecialMention)
          val xml = TestData.buildGBGBXml(insertXml)
          val result = service.ensureGuarantee(xml)
          normalise(result.right.get) mustEqual normalise(xml)
          validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
      }
    }

    "GB -> GB, with Reference Type, Has Reference Number, No Special Mentions, Currency & Value > 0.01 - should pass through unedited" in {
      forAll(Gen.oneOf(Guarantee.referenceTypes)) {
        typeNum =>
          val insertXml = TestData.guaranteeWithType(typeNum) ++ TestData.goodsWithCustomSpecialMention(Nil)
          val xml = TestData.buildGBGBXml(insertXml)
          val result = service.ensureGuarantee(xml)
          normalise(result.right.get) mustEqual normalise(xml)
          validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
      }
    }

    "GB -> GB, with Reference Type, Has Reference Number, Non-CAL Special Mentions - should pass through unedited" in {
      forAll(Gen.oneOf(Guarantee.referenceTypes)) {
        typeNum =>
          val custom =
            <SPEMENMT2>
              <ExpFroECMT24>4</ExpFroECMT24>
              <ExpFroCouMT25>az</ExpFroCouMT25>
            </SPEMENMT2>
          val insertXml = TestData.guaranteeWithType(typeNum) ++ TestData.goodsWithCustomSpecialMention(custom)
          val xml = TestData.buildGBGBXml(insertXml)
          val result = service.ensureGuarantee(xml)
          normalise(result.right.get) mustEqual normalise(xml)
          validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
      }
    }

    "GB -> EU, with Reference Type, Has Reference Number, No Special Mentions, multiple Guarantees - creates Special Mentions equal to number of guarantees" in {
      forAll(Gen.oneOf(Guarantee.referenceTypes)) {
      typeNum => {
          val insertXml = TestData.guaranteeWithType(typeNum, 1) ++ TestData.guaranteeWithType(typeNum, 2) ++ TestData.guaranteeWithType(typeNum, 3) ++ TestData.goodsWithCustomSpecialMention(Nil)
          val xml = TestData.buildGBEUXml(insertXml)
          val expectedSpecialMentions =
            <SPEMENMT2>
              <AddInfMT21>1.00GBP21GB0000010000HU1</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>1.00GBP21GB0000010000HU2</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>1.00GBP21GB0000010000HU3</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
          val expectedXml = TestData.buildGBEUXml(TestData.guaranteeWithType(typeNum, 1) ++ TestData.guaranteeWithType(typeNum, 2) ++ TestData.guaranteeWithType(typeNum, 3) ++ TestData.goodsWithCustomSpecialMention(expectedSpecialMentions))
          val result = service.ensureGuarantee(xml)
          normalise(result.right.get) mustEqual normalise(expectedXml)
          validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
        }
      }
    }

    "GB -> XI, type 1, CAL w/o Reference Number - SM Passes through to core unedited, new one added" in {
      val custom =
        <SPEMENMT2>
          <AddInfCodMT23>CAL</AddInfCodMT23>
        </SPEMENMT2>
      val insertXml = TestData.basicGuarantee ++ TestData.goodsWithCustomSpecialMention(custom)
      val xml = TestData.buildGBXIXml(insertXml)
      val expectedXml = TestData.buildGBXIXml(TestData.basicGuarantee ++ TestData.goodsWithCustomSpecialMention(custom ++ addedSpecialMention))
      val result = service.ensureGuarantee(xml)
      normalise(result.right.get) mustEqual normalise(expectedXml)
      validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
    }

    "GB -> XI, type 1, non-CAL Special Mentions - SM Passes through to core unedited" in {
      val custom =
        <SPEMENMT2>
          <ExpFroECMT24>4</ExpFroECMT24>
          <ExpFroCouMT25>az</ExpFroCouMT25>
        </SPEMENMT2>
      val insertXml = TestData.basicGuarantee ++ TestData.goodsWithCustomSpecialMention(addedSpecialMention ++ custom)
      val xml = TestData.buildGBXIXml(insertXml)
      val result = service.ensureGuarantee(xml)
      normalise(result.right.get) mustEqual normalise(xml)
      validator.validate(normalise(result.right.get), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
    }



  }

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movement-trader-at-destination.port")
}
