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

import config.DefaultGuaranteeConfig
import data.TestXml
import models.ParseError.{AmountWithoutCurrency, GuaranteeNotFound}
import models.{AddSpecialMentionInstruction, ChangeGuaranteeInstruction, GOOITEGDSNode, Guarantee, NoChangeGuaranteeInstruction, NoChangeInstruction, SpecialMention, SpecialMentionGuarantee, SpecialMentionOther, TransformInstructionSet}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import org.mockito.ArgumentMatchers.any

class InstructionBuilderSpec extends AnyFreeSpec with MockitoSugar with BeforeAndAfterEach with TestXml with Matchers with ScalaCheckPropertyChecks {

  val mockGIB: GuaranteeInstructionBuilder = mock[GuaranteeInstructionBuilder]

  override def beforeEach = {
    super.beforeEach()
    reset(mockGIB)
  }

  protected def baseApplicationBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm" -> false
      )

  def sut: InstructionBuilder = {
    val application = baseApplicationBuilder
      .overrides(
        bind[GuaranteeInstructionBuilder].toInstance(mockGIB)
      )
      .build()

    application.injector.instanceOf[InstructionBuilder]
  }

  "buildInstructionSet" - {

    "returns TransformInstructionSet with no instructions if no defaulting guarantees provided" in {
      val result = sut.buildInstructionSet(GOOITEGDSNode(1, Nil), Seq(Guarantee('A', "test")))
      result.right.get mustBe TransformInstructionSet(GOOITEGDSNode(1, Nil), Nil)
    }

    "returns TransformInstructionSet with an AddInstruction if no special mentions but a guarantee" in {
      when(mockGIB.buildInstructionFromGuarantee(any(), any())).thenReturn(Right(AddSpecialMentionInstruction(SpecialMentionGuarantee("test", Nil))))

      val result = sut.buildInstructionSet(GOOITEGDSNode(1, Nil),Seq(Guarantee('1',"test")))
      result.right.get mustBe TransformInstructionSet(GOOITEGDSNode(1, Nil), Seq(AddSpecialMentionInstruction(SpecialMentionGuarantee("test", Nil))))
    }

    "returns ParseError if the built instruction returns an error" in {
      when(mockGIB.buildInstructionFromGuarantee(any(), any())).thenReturn(Left(AmountWithoutCurrency("test")))

      val result = sut.buildInstructionSet(GOOITEGDSNode(1, Nil),Seq(Guarantee('1',"test")))
      result.left.get mustBe AmountWithoutCurrency("test")
    }



  }

  "findMatchingMention" - {
    val smSeq = Seq(SpecialMentionGuarantee("1alpha"), SpecialMentionGuarantee("2beta", Nil), SpecialMentionGuarantee("3charlie"))

    "returns (Some(SpecialMentionGuarantee), Guarantee) when a special mention ends with the guarantee gReference value" in {
      sut.findMatchingMention(Guarantee('1', "alpha"), smSeq) mustBe a[Tuple2[Some[SpecialMention], Guarantee]]
    }

    "returns None when no special mention ends with the guarantee gReference value" in {
      sut.findMatchingMention(Guarantee('1', "delta"), smSeq) mustBe a[Tuple2[None.type, Guarantee]]
    }
  }

}

class GuaranteeInstructionBuilderSpec extends AnyFreeSpec with MockitoSugar with BeforeAndAfterEach with TestXml with Matchers with ScalaCheckPropertyChecks {

  protected def baseApplicationBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm" -> false
      )

  override def beforeEach = {
    super.beforeEach()
  }

  val mockGuaranteeConfig = mock[DefaultGuaranteeConfig]
  val mockCurrency = "XYZ"
  val mockAmount = 11.00
  when(mockGuaranteeConfig.currency).thenReturn(mockCurrency)
  when(mockGuaranteeConfig.amount).thenReturn(mockAmount)

  def sut: GuaranteeInstructionBuilder = {
    val application = baseApplicationBuilder
        .overrides(bind[DefaultGuaranteeConfig].toInstance(mockGuaranteeConfig))
      .build()

    application.injector.instanceOf[GuaranteeInstructionBuilder]
  }

  "buildInstructionFromGuarantee" - {
    "returns Right(NoChangeGuaranteeInstruction) if guarantee type is not in referenceTypes" in {
      val gTypes = Seq('1', '2', '3', '4', '5', '6', '7')
      val excludedTypes = gTypes.diff(Guarantee.referenceTypes)

      excludedTypes.foreach {
        typeChar =>
          sut.buildInstructionFromGuarantee(Guarantee(typeChar, "alpha"), Some(SpecialMentionGuarantee("test alpha"))) mustBe a[Right[_, NoChangeGuaranteeInstruction]]
      }
    }

    "returns Left(AmountWithoutCurrency) if guarantee type is in referenceTypes and there is no currency value" in {
      Guarantee.referenceTypes.foreach {
        typeChar =>
          sut.buildInstructionFromGuarantee(Guarantee(typeChar, "alpha"), Some(SpecialMentionGuarantee("100.00alpha"))) mustBe a[Left[AmountWithoutCurrency, _]]
      }
    }

    "returns Right(NoChangeGuaranteeInstruction) if guarantee type is in referenceTypes and there is already an amount and currency" in {
      Guarantee.referenceTypes.foreach {
        typeChar =>
          val result = sut.buildInstructionFromGuarantee(Guarantee(typeChar, "alpha"), Some(SpecialMentionGuarantee("100.00EURalpha")))
          result mustBe a[Right[_, NoChangeGuaranteeInstruction]]
          result.right.get.asInstanceOf[NoChangeGuaranteeInstruction].mention.additionalInfo mustBe "100.00EURalpha"
      }
    }

    "returns Right(ChangeGuaranteeInstruction) to apply the default guarantee value if guarantee type is in referenceTypes and there is no amount but a currency" in {
      Guarantee.referenceTypes.foreach {
        typeChar =>
          val result = sut.buildInstructionFromGuarantee(Guarantee(typeChar, "alpha"), Some(SpecialMentionGuarantee("GBPalpha")))
          result mustBe a[Right[_, ChangeGuaranteeInstruction]]
          result.right.get.asInstanceOf[ChangeGuaranteeInstruction].mention.additionalInfo mustBe s"${BigDecimal(mockGuaranteeConfig.amount).setScale(2, BigDecimal.RoundingMode.UNNECESSARY).toString()}${mockCurrency}alpha"
      }
    }

    "returns Right(ChangeGuaranteeInstruction) to apply the default guarantee value if guarantee type is in referenceTypes and there is no amount and no currency" in {
      Guarantee.referenceTypes.foreach {
        typeChar =>
          val result = sut.buildInstructionFromGuarantee(Guarantee(typeChar, "alpha"), Some(SpecialMentionGuarantee("alpha")))
          result mustBe a[Right[_, ChangeGuaranteeInstruction]]
          result.right.get.asInstanceOf[ChangeGuaranteeInstruction].mention.additionalInfo mustBe s"${BigDecimal(mockGuaranteeConfig.amount).setScale(2, BigDecimal.RoundingMode.UNNECESSARY).toString()}${mockCurrency}alpha"
      }
    }

  }
}