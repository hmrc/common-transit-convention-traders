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

package services

import cats.data.ReaderT
import models.{ChangeGuaranteeInstruction, GOOITEGDSNode, Guarantee, NoChangeGuaranteeInstruction, NoChangeInstruction, ParseError, ParseHandling, SpecialMention, SpecialMentionGuarantee, SpecialMentionGuaranteeDetails, SpecialMentionOther, TransformInstructionSet}
import org.mockito.ArgumentMatchers.any
import data.TestXml
import models.ParseError.{GuaranteeNotFound, GuaranteeTypeInvalid, InvalidItemNumber}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import utils.guaranteeParsing.{GuaranteeXmlReaders, InstructionBuilder}

import scala.xml.{Node, NodeSeq}

class EnsureGuaranteeServiceSpec extends AnyFreeSpec with ParseHandling with MockitoSugar with BeforeAndAfterEach with TestXml with Matchers with ScalaCheckPropertyChecks {

  private val mockXmlReaders: GuaranteeXmlReaders = mock[GuaranteeXmlReaders]
  private val mockInstructionBuilder: InstructionBuilder = mock[InstructionBuilder]

  override def beforeEach = {
    super.beforeEach()
    reset(mockXmlReaders)
    reset(mockInstructionBuilder)
  }

  protected def baseApplicationBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm" -> false
      )

  def sut: EnsureGuaranteeService = {
    val application = baseApplicationBuilder
      .overrides(
        bind[GuaranteeXmlReaders].toInstance(mockXmlReaders),
        bind[InstructionBuilder].toInstance(mockInstructionBuilder)
      )
      .build()

    application.injector.instanceOf[EnsureGuaranteeService]
  }

  def fakeGooBlock(sms: Seq[SpecialMention]): GOOITEGDSNode = GOOITEGDSNode(1, sms)

  "ensureGuarantee" - {
    "returns parseError if parseInstructionSets has an error" in {
      when(mockXmlReaders.parseGuarantees(any()))
        .thenReturn(Left(GuaranteeTypeInvalid("test")))

      val result = sut.ensureGuarantee(<example></example>)
      result mustBe a[Left[GuaranteeTypeInvalid, _]]
    }

    "returns updatedNodeSeq if everything is ok" in {
      when(mockXmlReaders.parseGuarantees(any()))
        .thenReturn(Right(Seq(Guarantee(1, "test"))))

      when(mockXmlReaders.parseSpecialMentions(any()))
        .thenReturn(Right(Seq(SpecialMentionOther(<SPEMENMT2><test></test></SPEMENMT2>))))

      when(mockXmlReaders.gOOITEGDSNode)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]](_ => Right(Seq(GOOITEGDSNode(1, Seq(SpecialMentionOther(<test></test>)))))))

      when(mockXmlReaders.gOOITEGDSNodeFromNode)
        .thenReturn(ReaderT[ParseHandler, Node, GOOITEGDSNode](_ => Right(GOOITEGDSNode(1, Seq(SpecialMentionOther(<test></test>))))))

      when(mockInstructionBuilder.buildInstruction(any(), any()))
        .thenReturn(Right(NoChangeInstruction(<SPEMENMT2><test></test></SPEMENMT2>)))

      val result = sut.ensureGuarantee(
        //EXAMPLE XML
        <example><GOOITEGDS><IteNumGDS7>1</IteNumGDS7><SPEMENMT2><test></test></SPEMENMT2></GOOITEGDS></example>
      )
      result mustBe a[Right[_, NodeSeq]]
      result.right.get.toString() mustBe "<example><GOOITEGDS><IteNumGDS7>1</IteNumGDS7><SPEMENMT2><test></test></SPEMENMT2></GOOITEGDS></example>"
    }
  }

  "parseIntructionSets" - {
    "returns parseError if parseGuarantees has an error" in {
      when(mockXmlReaders.parseGuarantees(any()))
        .thenReturn(Left(GuaranteeTypeInvalid("test")))

      val result = sut.parseInstructionSets(<test></test>)
      result mustBe a[Left[GuaranteeTypeInvalid, _]]
    }

    "returns parseError if GOOITEGDSNode has an error" in {
      when(mockXmlReaders.parseGuarantees(any()))
        .thenReturn(Right(Seq(Guarantee(1, "test"))))

      when(mockXmlReaders.gOOITEGDSNode)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]](_ => Left(InvalidItemNumber("test"))))

      val result = sut.parseInstructionSets(<test><GOOITEGDS><IteNumGDS7>A</IteNumGDS7></GOOITEGDS></test>)
      result mustBe a[Left[InvalidItemNumber,_]]

    }

    "returns parseError if getInstructionSet returns with an error" in {
      when(mockXmlReaders.parseGuarantees(any()))
        .thenReturn(Right(Seq(Guarantee(1, "test"))))

      when(mockXmlReaders.gOOITEGDSNode)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]](_ => Right(Seq(GOOITEGDSNode(1, Seq(SpecialMentionOther(<test></test>)))))))

      when(mockInstructionBuilder.buildInstruction(any(), any()))
        .thenReturn(Left(GuaranteeNotFound("test")))

      val result = sut.parseInstructionSets(<test></test>)
      result mustBe a[Left[GuaranteeNotFound, _]]
    }

    "returns Seq[InstructionSet] if getInstructionSet returns with no error" in {
      when(mockXmlReaders.parseGuarantees(any()))
        .thenReturn(Right(Seq(Guarantee(1, "test"))))

      when(mockXmlReaders.gOOITEGDSNode)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]](_ => Right(Seq(GOOITEGDSNode(1, Seq(SpecialMentionOther(<test></test>)))))))

      when(mockInstructionBuilder.buildInstruction(any(), any()))
        .thenReturn(Right(NoChangeInstruction(<test></test>)))

      val result = sut.parseInstructionSets(<test></test>)
      result mustBe a[Right[_, TransformInstructionSet]]
      result.right.get mustBe Seq(
        TransformInstructionSet(GOOITEGDSNode(1, Seq(SpecialMentionOther(<test></test>))), Seq(NoChangeInstruction(<test></test>))))

    }
  }

  "buildFromInstruction" - {
    "returns NodeSeq based on the inputted instruction" in {
      val resultNoChange = sut.buildFromInstruction(NoChangeInstruction(<example></example>)).toString()
      resultNoChange.toString().filter(_ > ' ') mustBe
      "<example></example>".filter(_ > ' ')

      val resultNoChangeGuarantee = sut.buildFromInstruction(NoChangeGuaranteeInstruction(SpecialMentionGuarantee("testInfo")))
      resultNoChangeGuarantee.toString().filter(_ > ' ') mustBe
        "<SPEMENMT2><AddInfMT21>testInfo</AddInfMT21><AddInfCodMT23>CAL</AddInfCodMT23></SPEMENMT2>".filter(_ > ' ')


      val resultChangeGuarantee = sut.buildFromInstruction(ChangeGuaranteeInstruction(
        SpecialMentionGuaranteeDetails(BigDecimal(10000.00), "EUR", "testCode")
      ))
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

  "getInstructionSet" - {
    "returns ParseError when instructionBuilder returns a parsing error" in {
      when(mockInstructionBuilder.buildInstruction(any(), any()))
        .thenReturn(Left(GuaranteeNotFound("test")))

      val result = sut.getInstructionSet(fakeGooBlock(Seq(SpecialMentionOther(<example></example>))), Seq.empty[Guarantee])
      result mustBe a[Left[ParseError, _]]
    }

    "return TransformInstructionSet if no ParseErrors found" in {
      when(mockInstructionBuilder.buildInstruction(any(), any()))
        .thenReturn(Right(NoChangeGuaranteeInstruction(SpecialMentionGuarantee("test"))))

      val result = sut.getInstructionSet(fakeGooBlock(Seq(SpecialMentionOther(<example></example>))), Seq.empty[Guarantee])
      result mustBe a[Right[_, TransformInstructionSet]]
      result.right.get.instructions.length mustBe 1
    }

  }






}
