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
import models.ParseError._
import models._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class GuaranteeXmlReadersSpec extends AnyFreeSpec with TestXml with Matchers{

  val sut = new GuaranteeXmlReaders

  "gOOITEGDSNode" - {
    "returns Seq[GOOITEGDSNode] when no parse errors" in {
      val result = sut.gOOITEGDSNode(exampleGOOITEGDSSequence)
      result mustBe a[Right[_, Seq[GOOITEGDSNode]]]
      val gooBlocks = result.right.get
      val gooBlock = gooBlocks.head
      gooBlock.itemNumber mustBe 1
      gooBlock.specialMentions.length mustBe 4
      gooBlock.specialMentions.collect { case sm: SpecialMentionOther => sm }.length mustBe 1
      gooBlock.specialMentions.collect { case sm: SpecialMentionGuarantee => sm}.length mustBe 3
    }

    "returns InvalidItemNumber when itemNumber is not an int" in {
      val exampleGOOITEGDSSequenceInvalidItemNumber =
        <example>
          <GOOITEGDS>
            <IteNumGDS7>A</IteNumGDS7>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z1</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z3</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z9</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>EU_EXIT</AddInfMT21>
              <AddInfMT21LNG>EN</AddInfMT21LNG>
              <AddInfCodMT23>DG1</AddInfCodMT23>
              <ExpFroCouMT25>AD</ExpFroCouMT25>
            </SPEMENMT2>
          </GOOITEGDS>
        </example>

      sut.gOOITEGDSNode(exampleGOOITEGDSSequenceInvalidItemNumber) mustBe a[Left[InvalidItemNumber, _]]
    }

    "returns MissingItemNumber when itemNumber is missing" in {
      val exampleGOOITEGDSSequenceMissingItemNumber =
        <example>
          <GOOITEGDS>
            <IteNumGDS7></IteNumGDS7>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z1</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z3</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z9</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>EU_EXIT</AddInfMT21>
              <AddInfMT21LNG>EN</AddInfMT21LNG>
              <AddInfCodMT23>DG1</AddInfCodMT23>
              <ExpFroCouMT25>AD</ExpFroCouMT25>
            </SPEMENMT2>
          </GOOITEGDS>
        </example>

      sut.gOOITEGDSNode(exampleGOOITEGDSSequenceMissingItemNumber) mustBe a[Left[MissingItemNumber, _]]
    }

    "returns MissingItemNumber when itemNumber ode is missing" in {
      val exampleGOOITEGDSSequenceMissingItemNumberNode =
        <example>
          <GOOITEGDS>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z1</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z3</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z9</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>EU_EXIT</AddInfMT21>
              <AddInfMT21LNG>EN</AddInfMT21LNG>
              <AddInfCodMT23>DG1</AddInfCodMT23>
              <ExpFroCouMT25>AD</ExpFroCouMT25>
            </SPEMENMT2>
          </GOOITEGDS>
        </example>

      sut.gOOITEGDSNode(exampleGOOITEGDSSequenceMissingItemNumberNode) mustBe a[Left[MissingItemNumber, _]]
    }

    "returns ParseError when special mention is invalid" in {
      val exampleGOOITEGDSSequenceInvalidSpecialMention =
        <example>
          <GOOITEGDS>
            <IteNumGDS7>1</IteNumGDS7>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z1</AddInfMT21>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z3</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>7000.0EUR07IT00000100000Z9</AddInfMT21>
              <AddInfCodMT23>CAL</AddInfCodMT23>
            </SPEMENMT2>
            <SPEMENMT2>
              <AddInfMT21>EU_EXIT</AddInfMT21>
              <AddInfMT21LNG>EN</AddInfMT21LNG>
              <AddInfCodMT23>DG1</AddInfCodMT23>
              <ExpFroCouMT25>AD</ExpFroCouMT25>
            </SPEMENMT2>
          </GOOITEGDS>
        </example>

      sut.gOOITEGDSNode(exampleGOOITEGDSSequenceInvalidSpecialMention) mustBe a[Left[ParseErrorSpec, _]]
    }
  }


  "specialMention" - {
    "returns SpecialMentionGuarantee when AddInfCodMT23 is CAL" in {
      sut.specialMention(exampleGuaranteeSPEMENMT2) mustBe a[Right[_, SpecialMentionGuarantee]]
    }

    "returns SpecialMentionOther when AddInfCodMT23 is not CAL" in {
      sut.specialMention(exampleOtherSPEMENMT2) mustBe a[Right[_, SpecialMentionOther]]
    }

    "returns AdditionalInfoMissing when AddInfMT21 is empty" in {
      sut.specialMention(exampleAdditionalInfoMissing) mustBe a[Left[AdditionalInfoMissing, _]]
    }

    "returns AdditionalInfoCodeMissing when AddInfCodMT23 is empty" in {
      sut.specialMention(exampleCodeMissing) mustBe a[Left[AdditionalInfoCodeMissing, _]]
    }
  }

  "guarantee" - {
    "returns Guarantee when given GuaRefNumGRNREF1 field" in {
      val result = sut.guarantee(exampleGuaranteeGuaTypGUA1)
      result mustBe a[Right[_, Guarantee]]
      val item = result.right.get
      item.gReference mustEqual "07IT00000100000Z3"
      item.gType mustEqual 8
    }

    "returns GuaranteeTypeInvalid if no GuaTypGUA1 value" in {
      sut.guarantee(exampleGuaranteeGuaTypGUA1MissingGuaType) mustBe a[Left[GuaranteeTypeInvalid, _]]
    }

    "returns GuaranteeTypeInvalid if GuaTypGua1 is not an int" in {
      sut.guarantee(exampleGuaranteeGuaTypGUA1BadGuaType) mustBe a[Left[GuaranteeTypeInvalid, _]]
    }

    "returns NoGuaranteeReferenceNumber if GuaRefNumGRNRef1 was empty" in {
      sut.guarantee(exampleGuaranteeGuaTypGUA1EmptyReference) mustBe a[Left[NoGuaranteeReferenceNumber, _]]
    }
  }

  "parseSpecialMentions" - {
    "returns Seq[SpecialMention] when no parse errors" in {
      val result = sut.parseSpecialMentions(exampleGOOITEGDS)
      result mustBe a[Right[_, Seq[SpecialMention]]]
      result.right.get.length mustBe 4
    }

    "returns ParseError when any of the special mentions would fail" in {
      sut.parseSpecialMentions(exampleGOOITEGDSBadSpecial) mustBe a[Left[ParseErrorSpec, _]]
    }
  }

  "parseGuarantees" - {
    "returns Seq[Guarantee] when no parse errors" in {
      val example =
        <example>
          <GUAGUA>
            <GuaTypGUA1>8</GuaTypGUA1>
            <GUAREFREF>
              <GuaRefNumGRNREF1>07IT00000100000Z3</GuaRefNumGRNREF1>
            </GUAREFREF>
          </GUAGUA>
          <GUAGUA>
            <GuaTypGUA1>8</GuaTypGUA1>
            <GUAREFREF>
              <GuaRefNumGRNREF1>07IT00000100000Z4</GuaRefNumGRNREF1>
            </GUAREFREF>
          </GUAGUA>
        </example>

      val result = sut.parseGuarantees(example)
      result mustBe a[Right[_, Seq[Guarantee]]]
      result.right.get.length mustBe 2
    }

    "returns ParseError if any guarantees fail to parse" in {
      val exampleInvalid =
        <example>
          <GUAGUA>
            <GuaTypGUA1>8</GuaTypGUA1>
            <GUAREFREF>
              <GuaRefNumGRNREF1></GuaRefNumGRNREF1>
            </GUAREFREF>
          </GUAGUA>
          <GUAGUA>
            <GuaTypGUA1>8</GuaTypGUA1>
            <GUAREFREF>
              <GuaRefNumGRNREF1>07IT00000100000Z4</GuaRefNumGRNREF1>
            </GUAREFREF>
          </GUAGUA>
        </example>

      sut.parseGuarantees(exampleInvalid) mustBe a[Left[ParseErrorSpec, _]]
    }
  }

}