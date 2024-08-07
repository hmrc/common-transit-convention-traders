/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.implicits.catsSyntaxEitherId
import data.TestXml
import models.ParseError._
import models._
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class GuaranteeXmlReadersSpec extends AnyFreeSpec with TestXml with Matchers with ScalaCheckPropertyChecks {

  val sut = new GuaranteeXmlReaders

  "gOOITEGDSNode" - {
    "returns Seq[GOOITEGDSNode] when no parse errors" in {
      val result = sut.gOOITEGDSNode(exampleGOOITEGDSSequence)
      result mustBe a[Right[_, Seq[GOOITEGDSNode]]]
      result.map {
        gooBlocks =>
          val gooBlock = gooBlocks.head
          gooBlock.itemNumber mustBe 1
          gooBlock.specialMentions.length mustBe 4
          gooBlock.specialMentions.collect {
            case sm: SpecialMentionOther => sm
          }.length mustBe 1
          gooBlock.specialMentions.collect {
            case sm: SpecialMentionGuarantee => sm
          }.length mustBe 3
      }
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

    "returns SpecialMentionOther (so it passes through to core) when special mention lacks AddInfCodMT23" in {
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

      val result = sut.gOOITEGDSNode(exampleGOOITEGDSSequenceInvalidSpecialMention)
      result mustBe a[Right[_, Seq[GOOITEGDSNode]]]
      result.map {
        gooBlocks =>
          val gooBlock = gooBlocks.head
          gooBlock.specialMentions.length mustBe 4
          gooBlock.specialMentions.collect {
            case sm: SpecialMentionOther => sm
          }.length mustBe 2
          gooBlock.specialMentions.collect {
            case sm: SpecialMentionGuarantee => sm
          }.length mustBe 2
      }
    }
  }

  "gOOITEGDSNodeFromNode" - {
    "returns GOOITEGDSNode when no parse errors" in {
      val result = sut.gOOITEGDSNodeFromNode(exampleGOOITEGDS)
      result mustBe a[Right[_, GOOITEGDSNode]]
      result.map {
        gooBlock =>
          gooBlock.itemNumber mustBe 1
          gooBlock.specialMentions.length mustBe 4
          gooBlock.specialMentions.collect {
            case sm: SpecialMentionOther => sm
          }.length mustBe 1
          gooBlock.specialMentions.collect {
            case sm: SpecialMentionGuarantee => sm
          }.length mustBe 3
      }
    }

    "returns InvalidItemNumber when itemNumber is not an int" in {
      val exampleGOOITEGDSSequenceInvalidItemNumber =
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

      sut.gOOITEGDSNodeFromNode(exampleGOOITEGDSSequenceInvalidItemNumber) mustBe a[Left[InvalidItemNumber, _]]
    }

    "returns MissingItemNumber when itemNumber is missing" in {
      val exampleGOOITEGDSSequenceMissingItemNumber =
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

      sut.gOOITEGDSNodeFromNode(exampleGOOITEGDSSequenceMissingItemNumber) mustBe a[Left[MissingItemNumber, _]]
    }

    "returns MissingItemNumber when itemNumber ode is missing" in {
      val exampleGOOITEGDSSequenceMissingItemNumberNode =
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

      sut.gOOITEGDSNodeFromNode(exampleGOOITEGDSSequenceMissingItemNumberNode) mustBe a[Left[MissingItemNumber, _]]
    }

    "returns SpecialMentionOther (so it passes through to core) when special mention lacks AddInfCodMT23" in {
      val exampleGOOITEGDSSequenceInvalidSpecialMention =
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

      val result = sut.gOOITEGDSNodeFromNode(exampleGOOITEGDSSequenceInvalidSpecialMention)
      result mustBe a[Right[_, GOOITEGDSNode]]
      result.map {
        gooBlock =>
          gooBlock.specialMentions.length mustBe 4
          gooBlock.specialMentions.collect {
            case sm: SpecialMentionOther => sm
          }.length mustBe 2
          gooBlock.specialMentions.collect {
            case sm: SpecialMentionGuarantee => sm
          }.length mustBe 2
      }
    }
  }

  "specialMention" - {
    "returns SpecialMentionGuarantee when AddInfCodMT23 is CAL" in {
      sut.specialMention(exampleGuaranteeSPEMENMT2) mustBe a[Right[_, SpecialMentionGuarantee]]
    }

    "returns SpecialMentionOther when AddInfCodMT23 is not CAL" in {
      sut.specialMention(exampleOtherSPEMENMT2) mustBe a[Right[_, SpecialMentionOther]]
    }

    "returns SpecialMentionOther when AddInfMT21 is empty" in {
      sut.specialMention(exampleAdditionalInfoMissing) mustBe a[Right[_, SpecialMentionOther]]
    }

    "returns SpecialMentionOther when AddInfCodMT23 is empty" in {
      sut.specialMention(exampleCodeMissing) mustBe a[Right[_, SpecialMentionOther]]
    }
  }

  "guaranteeSet" - {
    val referenceTypeGen = Gen.oneOf(Guarantee.referenceTypes)
    val otherTypes       = Gen.oneOf(Guarantee.validTypes.diff(Guarantee.referenceTypes))
    "returns a single Guarantee when given a single GuaRefNumGRNREF1 field and the Guarantee type is 0,1,2,4,9" in {
      forAll(referenceTypeGen) {
        gType =>
          val result = sut.guaranteeSet(exampleGuaranteeGuaTypGUA1(gType))
          result mustBe a[Right[_, Seq[Guarantee]]]
          result.map {
            guarantees =>
              guarantees.length mustBe 1
              guarantees.map {
                item =>
                  item.gReference mustEqual "07IT00000100000Z3"
                  item.gType mustEqual gType
              }
          }
      }
    }

    "returns a single Guarantee when given a single OthGuaRefREF4 field and the Guarantee type is not 0,1,2,4,9" in {
      forAll(otherTypes) {
        gType =>
          val result = sut.guaranteeSet(exampleOtherGuaranteeGuaTypGUA1(gType))
          result mustBe a[Right[_, Seq[Guarantee]]]
          result.map {
            guarantees =>
              guarantees.length mustBe 1
              guarantees.map {
                item =>
                  item.gReference mustEqual "SomeValue"
                  item.gType mustEqual gType
              }
          }
      }
    }

    "returns NoOtherGuaranteeField when not given any OthGuaRefREF4 and the guarantee type is not 0,1,2,4,9" in {
      forAll(otherTypes) {
        gType =>
          val result = sut.guaranteeSet(exampleGuaranteeGuaTypGUA1(gType))
          result mustBe a[Left[NoOtherGuaranteeField, _]]
      }
    }

    "returns GuaranteeTypeInvalid if no GuaTypGUA1 value" in {
      sut.guaranteeSet(exampleGuaranteeGuaTypGUA1MissingGuaType) mustBe a[Left[GuaranteeTypeInvalid, _]]
    }

    "returns GuaranteeTypeInvalid if GuaTypGua1 is not an int" in {
      sut.guaranteeSet(exampleGuaranteeGuaTypGUA1BadGuaType) mustBe a[Left[GuaranteeTypeInvalid, _]]
    }

    "returns NoGuaranteeReferenceNumber when not given any GuaRefNumGRNRef1 value" in {
      sut.guaranteeSet(exampleGuaranteeGuaTypGUA1EmptyReference) mustBe a[Left[NoGuaranteeReferenceNumber, _]]
    }

    "returns multiple Guarantees of the same type when multiple guarantee references provided (defaulting reference)" in {
      val gType  = '9'
      val xml    = exampleMultiGuaranteeGuaTypGUA1(gType, 4)
      val result = sut.guaranteeSet(xml)
      result mustBe a[Right[_, Seq[Guarantee]]]
      result.map {
        guarantees =>
          guarantees.length mustBe 4
          guarantees.map {
            item =>
              item.gReference mustEqual "07IT00000100000Z3"
              item.gType mustEqual gType
          }
      }
    }

    "returns multiple Guarantees of the same type when multiple guarantee references provided (non-defaulting reference)" in {
      val gType  = 'A'
      val xml    = exampleMultiGuaranteeGuaTypGUA1(gType, 4)
      val result = sut.guaranteeSet(xml)
      result mustBe a[Right[_, Seq[Guarantee]]]
      result.map {
        guarantees =>
          guarantees.length mustBe 4
          guarantees.map {
            item =>
              item.gReference mustEqual "SomeValue"
              item.gType mustEqual gType
          }
      }
    }
  }

  "parseSpecialMentions" - {
    "returns Seq[SpecialMention] when no parse errors" in {
      val result = sut.parseSpecialMentions(exampleGOOITEGDS)
      result mustBe a[Right[_, Seq[SpecialMention]]]
      result.map(_.length mustBe 4)
    }
  }

  "parseGuarantees" - {
    "returns Seq[Guarantee] when no parse errors" in {
      val example =
        <example>
          <GUAGUA>
            <GuaTypGUA1>2</GuaTypGUA1>
            <GUAREFREF>
              <GuaRefNumGRNREF1>07IT00000100000Z3</GuaRefNumGRNREF1>
            </GUAREFREF>
          </GUAGUA>
          <GUAGUA>
            <GuaTypGUA1>2</GuaTypGUA1>
            <GUAREFREF>
              <GuaRefNumGRNREF1>07IT00000100000Z4</GuaRefNumGRNREF1>
            </GUAREFREF>
          </GUAGUA>
        </example>

      val result = sut.parseGuarantees(example)
      result mustBe a[Right[_, Seq[Guarantee]]]
      result.map(_.length mustBe 2)
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

  "officeOfDeparture" - {
    val emptySample    = <example><CUSOFFDEPEPT><RefNumEPT1></RefNumEPT1></CUSOFFDEPEPT></example>
    val completeSample = <example><CUSOFFDEPEPT><RefNumEPT1>value</RefNumEPT1></CUSOFFDEPEPT></example>
    "returns DepartureEmpty if RefNumEPT1 is empty" in {
      val result = sut.officeOfDeparture(emptySample)
      result mustBe a[Left[DepartureEmpty, _]]
    }

    "returns the DepartureOffice if RefNumEPT1 has a value" in {
      val result = sut.officeOfDeparture(completeSample)
      result mustBe a[Right[_, DepartureOffice]]
      result mustBe DepartureOffice("value").asRight
    }
  }

  "officeOfDestination" - {
    val emptySample    = <example><CUSOFFDESEST><RefNumEST1></RefNumEST1></CUSOFFDESEST></example>
    val completeSample = <example><CUSOFFDESEST><RefNumEST1>value</RefNumEST1></CUSOFFDESEST></example>
    "returns DestinationEmpty if RefNumEPT1 is empty" in {
      val result = sut.officeOfDestination(emptySample)
      result mustBe a[Left[DepartureEmpty, _]]
    }

    "returns the DestinationOffice if RefNumEPT1 has a value" in {
      val result = sut.officeOfDestination(completeSample)
      result mustBe DestinationOffice("value").asRight
    }
  }

}
