/*
 * Copyright 2022 HM Revenue & Customs
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
import data.TestXml
import models.ParseError.AmountWithoutCurrency
import models.ParseError.DepartureEmpty
import models.ParseError.GuaranteeTypeInvalid
import models.ParseError.InvalidItemNumber
import models._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import utils.guaranteeParsing.GuaranteeXmlReaders
import utils.guaranteeParsing.InstructionBuilder
import utils.guaranteeParsing.RouteChecker
import utils.guaranteeParsing.XmlBuilder

import scala.xml.Node
import scala.xml.NodeSeq

class EnsureGuaranteeServiceSpec
    extends AnyFreeSpec
    with ParseHandling
    with MockitoSugar
    with BeforeAndAfterEach
    with TestXml
    with Matchers
    with ScalaCheckPropertyChecks {

  private val mockXmlReaders: GuaranteeXmlReaders        = mock[GuaranteeXmlReaders]
  private val mockInstructionBuilder: InstructionBuilder = mock[InstructionBuilder]
  private val mockRouteChecker: RouteChecker             = mock[RouteChecker]
  private val mockXmlBuilder: XmlBuilder                 = mock[XmlBuilder]

  override def beforeEach = {
    super.beforeEach()
    reset(mockXmlReaders)
    reset(mockInstructionBuilder)
    reset(mockRouteChecker)
    reset(mockXmlBuilder)
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
        bind[InstructionBuilder].toInstance(mockInstructionBuilder),
        bind[RouteChecker].toInstance(mockRouteChecker),
        bind[XmlBuilder].toInstance(mockXmlBuilder)
      )
      .build()

    application.injector.instanceOf[EnsureGuaranteeService]
  }

  def fakeGooBlock(sms: Seq[SpecialMention]): GOOITEGDSNode = GOOITEGDSNode(1, sms)

  "ensureGuarantee" - {

    "returns parseError if parseInstructionSets has an error" in {
      when(mockRouteChecker.gbOnlyCheck(any()))
        .thenReturn(Right(false))
      when(mockXmlReaders.parseGuarantees(any()))
        .thenReturn(Left(GuaranteeTypeInvalid("test")))

      val result = sut.ensureGuarantee(<example></example>)
      result mustBe a[Left[GuaranteeTypeInvalid, _]]
    }

    "returns updatedNodeSeq if everything is ok" in {
      when(mockRouteChecker.gbOnlyCheck(any()))
        .thenReturn(Right(false))

      when(mockXmlReaders.parseGuarantees(any()))
        .thenReturn(Right(Seq(Guarantee(1, "test"))))

      when(mockXmlReaders.parseSpecialMentions(any()))
        .thenReturn(Right(Seq(SpecialMentionGuarantee("test", Nil))))

      when(mockXmlReaders.gOOITEGDSNode)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]](
            _ => Right(Seq(GOOITEGDSNode(1, Seq(SpecialMentionGuarantee("test", Nil)))))
          )
        )

      when(mockXmlReaders.gOOITEGDSNodeFromNode)
        .thenReturn(
          ReaderT[ParseHandler, Node, GOOITEGDSNode](
            _ => Right(GOOITEGDSNode(1, Seq(SpecialMentionGuarantee("test", Nil))))
          )
        )

      when(mockInstructionBuilder.buildInstructionSet(any(), any())).thenReturn(
        Right(
          TransformInstructionSet(
            GOOITEGDSNode(1, Seq(SpecialMentionGuarantee("test", Nil))),
            Seq(NoChangeGuaranteeInstruction(SpecialMentionGuarantee("test", Nil)))
          )
        )
      )

      when(mockXmlBuilder.buildFromInstruction(any())).thenReturn(<SPEMENMT2><test></test></SPEMENMT2>)

      val result = sut.ensureGuarantee(
        //EXAMPLE XML
        <example><GOOITEGDS><IteNumGDS7>1</IteNumGDS7><SPEMENMT2><test></test></SPEMENMT2></GOOITEGDS></example>
      )
      result mustBe a[Right[_, NodeSeq]]
      result.right.get.toString() mustBe "<example><GOOITEGDS><IteNumGDS7>1</IteNumGDS7><SPEMENMT2><test></test></SPEMENMT2></GOOITEGDS></example>"
    }

    "returns parseError if gbOnlyCheck returns an error" in {
      when(mockRouteChecker.gbOnlyCheck(any()))
        .thenReturn(Left(DepartureEmpty("test")))

      val result = sut.ensureGuarantee(<example></example>)
      result mustBe a[Left[DepartureEmpty, _]]
    }

    "returns xml unchanged if gbOnlyCheck returns true" in {
      when(mockRouteChecker.gbOnlyCheck(any()))
        .thenReturn(Right(true))

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
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]](
            _ => Left(InvalidItemNumber("test"))
          )
        )

      val result = sut.parseInstructionSets(<test><GOOITEGDS><IteNumGDS7>A</IteNumGDS7></GOOITEGDS></test>)
      result mustBe a[Left[InvalidItemNumber, _]]

    }

    "returns parseError if getInstructionSet returns with an error" in {
      when(mockXmlReaders.parseGuarantees(any()))
        .thenReturn(Right(Seq(Guarantee(1, "test"))))

      when(mockXmlReaders.gOOITEGDSNode)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]](
            _ => Right(Seq(GOOITEGDSNode(1, Seq(SpecialMentionOther(<test></test>)))))
          )
        )

      when(mockInstructionBuilder.buildInstructionSet(any(), any()))
        .thenReturn(Left(AmountWithoutCurrency("test")))

      val result = sut.parseInstructionSets(<test></test>)
      result mustBe a[Left[AmountWithoutCurrency, _]]
    }

    "returns Seq[InstructionSet] if getInstructionSet returns with no error" in {
      when(mockXmlReaders.parseGuarantees(any()))
        .thenReturn(Right(Seq(Guarantee(1, "test"))))

      when(mockXmlReaders.gOOITEGDSNode)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]](
            _ => Right(Seq(GOOITEGDSNode(1, Seq(SpecialMentionOther(<test></test>)))))
          )
        )

      when(mockInstructionBuilder.buildInstructionSet(any(), any()))
        .thenReturn(Right(TransformInstructionSet(GOOITEGDSNode(1, Seq(SpecialMentionOther(<test></test>))), Seq(NoChangeInstruction(<test></test>)))))

      val result = sut.parseInstructionSets(<test></test>)
      result mustBe a[Right[_, TransformInstructionSet]]
      result.right.get mustBe Seq(TransformInstructionSet(GOOITEGDSNode(1, Seq(SpecialMentionOther(<test></test>))), Seq(NoChangeInstruction(<test></test>))))

    }
  }
}
