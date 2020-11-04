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
import models.ParseError.{GuaranteeAmountZero, GuaranteeNotFound}
import models.{ChangeGuaranteeInstruction, Guarantee, NoChangeGuaranteeInstruction, NoChangeInstruction, ParseError, ParseErrorSpec, SpecialMention, SpecialMentionGuarantee, SpecialMentionGuaranteeDetails, SpecialMentionOther, TransformInstruction, TransformInstructionSet}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder

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

  val mockDefaultApplier = mock[DefaultGuaranteeApplier]

  protected def baseApplicationBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm" -> false
      )

  override def beforeEach = {
    super.beforeEach()
    reset(mockDefaultApplier)
  }

  def sut: GuaranteeInstructionBuilder = {
    val application = baseApplicationBuilder
      .overrides(
        bind[DefaultGuaranteeApplier].toInstance(mockDefaultApplier)
      )
      .build()

    application.injector.instanceOf[GuaranteeInstructionBuilder]
  }

  "buildInstructionFromGuarantee" - {
    "returns Right(NoChangeGuaranteeInstruction) if guarantee type is not in concernedTypes" in {
      val gTypes = Seq(1, 2, 3, 4, 5, 6, 7)
      val excludedTypes = gTypes.diff(sut.concernedTypes)

      excludedTypes.foreach {
        typeNumber =>
          sut.buildInstructionFromGuarantee(Guarantee(typeNumber, "alpha"), SpecialMentionGuarantee("test alpha")) mustBe a[Right[_, NoChangeGuaranteeInstruction]]
      }
    }

    "returns ParseError if applyDefaultGuarantee returns one" in {
      when(mockDefaultApplier.applyDefaultGuarantee(any(), any()))
        .thenReturn(Left(GuaranteeAmountZero("test")))

      val result = sut.buildInstructionFromGuarantee(Guarantee(0, "alpha"), SpecialMentionGuarantee("100.00EURalphabetacharliedeltaepsilon"))
      result mustBe a[Left[ParseErrorSpec, _]]
    }

    "returns ChangeGuaranteeInstruction if applyDefaultGuarantee is successful" in {
      when(mockDefaultApplier.applyDefaultGuarantee(any(), any()))
        .thenReturn(Right(SpecialMentionGuaranteeDetails(BigDecimal(100.00), "EUR", "test")))

      val result = sut.buildInstructionFromGuarantee(Guarantee(1, "alpha"), SpecialMentionGuarantee("alpha"))
      result mustBe a[Right[_, ChangeGuaranteeInstruction]]
    }
  }
}