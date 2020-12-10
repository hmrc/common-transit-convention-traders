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

import org.mockito.ArgumentMatchers.any
import data.TestXml
import models.ParseError.{AmountWithoutCurrency, GuaranteeAmountZero, GuaranteeNotFound}
import models.{ChangeGuaranteeInstruction, Guarantee, NoChangeGuaranteeInstruction, NoChangeInstruction, ParseError, ParseErrorSpec, SpecialMention, SpecialMentionGuarantee, SpecialMentionGuaranteeDetails, SpecialMentionOther, TransformInstruction, TransformInstructionSet}
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

  "buildInstruction" - {

    "returns NoChangeInstruction when given a SpecialMentionOther" in {
      val result = sut.buildInstruction(SpecialMentionOther(<example></example>), Seq.empty[Guarantee])
      result mustBe a[Right[_, NoChangeInstruction]]
    }

    "returns GuaranteeNotFound when unable to find a matching guarantee" in {
      val result = sut.buildInstruction(SpecialMentionGuarantee("test"), Seq.empty[Guarantee])
      result mustBe a[Left[GuaranteeNotFound, _]]
    }

    "returns ParseError when unable to build an instruction from the guarantee" in {
      when(mockGIB.buildInstructionFromGuarantee(any(), any()))
        .thenReturn(Left(GuaranteeAmountZero("test")))

      val result = sut.buildInstruction(SpecialMentionGuarantee("test"), Seq(Guarantee(1, "test")))
      result mustBe a[Left[ParseError, _]]
    }

    "returns TransformInstruction when one is produced by the GuaranteeInstructionBuilder" in {
      val sm = SpecialMentionGuarantee("test")

      when(mockGIB.buildInstructionFromGuarantee(any(), any()))
        .thenReturn(Right(NoChangeGuaranteeInstruction(sm)))

      val result = sut.buildInstruction(sm, Seq(Guarantee(1, "test")))
      result mustBe a[Right[_, TransformInstruction]]
    }

  }

  "pair" - {
    val gSeq = Seq(Guarantee(1, "alpha"), Guarantee(2, "beta"), Guarantee(3, "charlie"))

    "returns Some((SpecialMentionGuarantee, Guarantee)) when a guarantee ends with the SpecialMention gReference value" in {
      sut.pair(SpecialMentionGuarantee("test alpha"), gSeq) mustBe a[Some[SpecialMention]]
    }

    "returns None when no guarantee ends with the reference value" in {
      sut.pair(SpecialMentionGuarantee("test delta"), gSeq).isDefined mustBe false
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

  def sut: GuaranteeInstructionBuilder = {
    val application = baseApplicationBuilder
      .build()

    application.injector.instanceOf[GuaranteeInstructionBuilder]
  }

  "buildInstructionFromGuarantee" - {
    "returns Right(NoChangeGuaranteeInstruction) if guarantee type is not in referenceTypes" in {
      val gTypes = Seq('1', '2', '3', '4', '5', '6', '7')
      val excludedTypes = gTypes.diff(Guarantee.referenceTypes)

      excludedTypes.foreach {
        typeChar =>
          sut.buildInstructionFromGuarantee(Guarantee(typeChar, "alpha"), SpecialMentionGuarantee("test alpha")) mustBe a[Right[_, NoChangeGuaranteeInstruction]]
      }
    }

    "returns Left(AmountWithoutCurrency) if guarantee type is in referenceTypes and there is no currency value" in {
      Guarantee.referenceTypes.foreach {
        typeChar =>
          sut.buildInstructionFromGuarantee(Guarantee(typeChar, "alpha"), SpecialMentionGuarantee("100.00alpha")) mustBe a[Left[AmountWithoutCurrency, _]]
      }
    }

    "returns Right(NoChangeGuaranteeInstruction) if guarantee type is in referenceTypes and there is already an amount and currency" in {
      Guarantee.referenceTypes.foreach {
        typeChar =>
          val result = sut.buildInstructionFromGuarantee(Guarantee(typeChar, "alpha"), SpecialMentionGuarantee("100.00EURalpha"))
          result mustBe a[Right[_, NoChangeGuaranteeInstruction]]
          result.right.get.asInstanceOf[NoChangeGuaranteeInstruction].mention.additionalInfo mustBe "100.00EURalpha"
      }
    }

    "returns Right(ChangeGuaranteeInstruction) to apply the default guarantee value if guarantee type is in referenceTypes and there is no amount but a currency" in {
      Guarantee.referenceTypes.foreach {
        typeChar =>
          val result = sut.buildInstructionFromGuarantee(Guarantee(typeChar, "alpha"), SpecialMentionGuarantee("GBPalpha"))
          result mustBe a[Right[_, ChangeGuaranteeInstruction]]
          result.right.get.asInstanceOf[ChangeGuaranteeInstruction].mention.additionalInfo mustBe "10000.00EURalpha"
      }
    }

    "returns Right(ChangeGuaranteeInstruction) to apply the default guarantee value if guarantee type is in referenceTypes and there is no amount and no currency" in {
      Guarantee.referenceTypes.foreach {
        typeChar =>
          val result = sut.buildInstructionFromGuarantee(Guarantee(typeChar, "alpha"), SpecialMentionGuarantee("alpha"))
          result mustBe a[Right[_, ChangeGuaranteeInstruction]]
          result.right.get.asInstanceOf[ChangeGuaranteeInstruction].mention.additionalInfo mustBe "10000.00EURalpha"
      }
    }

  }
}