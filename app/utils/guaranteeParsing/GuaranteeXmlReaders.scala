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

import cats.syntax.all._
import models.ParseError._
import models._

import scala.xml.Node
import scala.xml.NodeSeq

class GuaranteeXmlReaders extends ParseHandling {

  def parseGuarantees(xml: NodeSeq): ParseHandler[Seq[Guarantee]] =
    (xml \ "GUAGUA").toList.traverse(guarantee(_))

  def parseSpecialMentions(xml: NodeSeq): ParseHandler[Seq[SpecialMention]] =
    (xml \ "SPEMENMT2").toList.traverse(specialMention(_))

  def gOOITEGDSNode(xml: NodeSeq): ParseHandler[Seq[GOOITEGDSNode]] =
    (xml \ "GOOITEGDS").toList.traverse(gOOITEGDSNodeFromNode(_))

  def gOOITEGDSNodeFromNode(node: Node): ParseHandler[GOOITEGDSNode] =
    for {
      itemNumber <- Either
        .catchOnly[NumberFormatException]((node \ "IteNumGDS7").text.toInt)
        .leftMap(
          _ => InvalidItemNumber("Invalid Item Number")
        )

      mentions <- parseSpecialMentions(node)

    } yield GOOITEGDSNode(itemNumber, mentions)

  def specialMention(xml: Node): ParseHandler[SpecialMention] = {
    val addInfMT21    = xml \ "AddInfMT21"
    val addInfCodMT23 = xml \ "AddInfCodMT23"
    Right {
      if (addInfMT21.nonEmpty && addInfCodMT23.text == "CAL")
        SpecialMentionGuarantee(addInfMT21.text, xml)
      else
        SpecialMentionOther(xml)
    }
  }

  def guarantee(xml: Node): ParseHandler[Guarantee] =
    (xml \ "GuaTypGUA1").text match {
      case gType if gType.isEmpty                              => Left(GuaranteeTypeInvalid("GuaTypGUA1 was invalid"))
      case gType if gType.length > 1                           => Left(GuaranteeTypeTooLong("GuaTypGUA1 was too long"))
      case gType if !Guarantee.validTypes.contains(gType.head) => Left(GuaranteeTypeInvalid("GuaTypGUA1 was not a valid type"))
      case gType if Guarantee.validTypes.contains(gType.head) =>
        val gChar = gType.head
        if (Guarantee.isOther(gChar)) {
          val gOther = (xml \ "GUAREFREF" \ "OthGuaRefREF4").text
          Either.cond(gOther.nonEmpty, Guarantee(gChar, gOther), NoOtherGuaranteeField("OthGuaRefREF4 was empty"))
        } else {
          val gReference = (xml \ "GUAREFREF" \ "GuaRefNumGRNREF1").text
          Either.cond(gReference.nonEmpty, Guarantee(gChar, gReference), NoGuaranteeReferenceNumber("GuaRefNumGRNREF1 was empty"))
        }
    }

  def officeOfDeparture(xml: NodeSeq): ParseHandler[DepartureOffice] = {
    val departure = (xml \ "CUSOFFDEPEPT" \ "RefNumEPT1").text
    Either.cond(departure.nonEmpty, DepartureOffice(departure), DepartureEmpty("Departure Empty"))
  }

  def officeOfDestination(xml: NodeSeq): ParseHandler[DestinationOffice] = {
    val destination = (xml \ "CUSOFFDESEST" \ "RefNumEST1").text
    Either.cond(destination.nonEmpty, DestinationOffice(destination), DestinationEmpty("Destination Empty"))
  }
}
