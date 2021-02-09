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

import cats.data.ReaderT
import models.ParseError._
import models._

import scala.util.{Failure, Success, Try}
import scala.xml.{Node, NodeSeq}

class GuaranteeXmlReaders extends ParseHandling {

  def parseGuarantees(xml: NodeSeq): ParseHandler[Seq[Guarantee]] = {

    val guaranteeEithers: Seq[Either[ParseError, Guarantee]] =
      (xml \ "GUAGUA").map {
        node => guarantee(node)
      }

    ParseError.sequenceErrors(guaranteeEithers)
  }

  def parseSpecialMentions(xml: NodeSeq): ParseHandler[Seq[SpecialMention]] = {
    ParseError.sequenceErrors((xml \ "SPEMENMT2").map {
      node =>
        specialMention(node)
    })
  }

  val gOOITEGDSNode: ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]] =
    ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]](xml => {
      ParseError.sequenceErrors((xml \ "GOOITEGDS" ).map {
        node => {
          val itemNumberNode = (node \ "IteNumGDS7")
          if(itemNumberNode.nonEmpty)
          {
            val itemNumberString = itemNumberNode.text
            if(itemNumberString.nonEmpty)
            {
              Try(itemNumberString.toInt) match {
                case Failure(_) => Left(InvalidItemNumber("Invalid Item Number"))
                case Success(itemNumber) =>
                  parseSpecialMentions(node) match {
                    case Left(error) => Left(error)
                    case Right(mentions) => Right(GOOITEGDSNode(itemNumber, mentions))
                  }
              }
            }
            else {
              Left(MissingItemNumber("Missing Item Number"))
            }
          }
          else {
            Left(MissingItemNumber("Missing Item Number"))
          }

        }
      })
    })

  val gOOITEGDSNodeFromNode: ReaderT[ParseHandler, Node, GOOITEGDSNode] =
    ReaderT[ParseHandler, Node, GOOITEGDSNode](node => {
          val itemNumberNode = (node \ "IteNumGDS7")
          if(itemNumberNode.nonEmpty)
          {
            val itemNumberString = itemNumberNode.text
            if(itemNumberString.nonEmpty)
            {
              Try(itemNumberString.toInt) match {
                case Failure(_) => Left(InvalidItemNumber("Invalid Item Number"))
                case Success(itemNumber) =>
                  parseSpecialMentions(node) match {
                    case Left(error) => Left(error)
                    case Right(mentions) => Right(GOOITEGDSNode(itemNumber, mentions))
                  }
              }
            }
            else {
              Left(MissingItemNumber("Missing Item Number"))
            }
          }
          else {
            Left(MissingItemNumber("Missing Item Number"))
          }
    })


  val specialMention: ReaderT[ParseHandler, Node, SpecialMention] = {
    ReaderT[ParseHandler, Node, SpecialMention](xml => {
      (xml \ "AddInfMT21").text match {
        case additionalInfo if additionalInfo.isEmpty => Left(AdditionalInfoMissing("AddInfMT21 field is missing"))
        case additionalInfo => (xml \ "AddInfCodMT23").text match {
          case code if code.isEmpty => Left(AdditionalInfoCodeMissing("AddInfCodMT23 is missing"))
          case "CAL" => Right(SpecialMentionGuarantee(additionalInfo))
          case _ => Right(SpecialMentionOther(xml))
        }
      }})
  }

  val guarantee: ReaderT[ParseHandler, Node, Guarantee] =
    ReaderT[ParseHandler, Node, Guarantee](xml => {
      (xml \ "GuaTypGUA1").text match {
        case gType if gType.isEmpty => Left(GuaranteeTypeInvalid("GuaTypGUA1 was invalid"))
        case gType if gType.length > 1 => Left(GuaranteeTypeTooLong("GuaTypGUA1 was too long"))
        case gType if !Guarantee.validTypes.contains(gType.head) => Left(GuaranteeTypeInvalid("GuaTypGUA1 was not a valid type"))
        case gType if Guarantee.validTypes.contains(gType.head) => {
          val gChar = gType.head
            if (Guarantee.isOther(gChar)) {
              (xml \ "GUAREFREF" \ "OthGuaRefREF4").text match {
                case gOther if !gOther.isEmpty => Right(Guarantee(gChar, gOther))
                case _ => Left(NoOtherGuaranteeField("OthGuaRefREF4 was empty"))
              }
            }
            else {
              (xml \ "GUAREFREF" \ "GuaRefNumGRNREF1").text match {
                case gReference if !gReference.isEmpty => Right(Guarantee(gChar, gReference))
                case _ => Left(NoGuaranteeReferenceNumber("GuaRefNumGRNREF1 was empty"))
              }
            }}}})

  val officeOfDeparture: ReaderT[ParseHandler, NodeSeq, DepartureOffice] =
    ReaderT[ParseHandler, NodeSeq, DepartureOffice](xml => {
      (xml \ "CUSOFFDEPEPT" \ "RefNumEPT1").text match {
        case departure if departure.isEmpty =>Left(DepartureEmpty("Departure Empty"))
        case departure => Right(DepartureOffice(departure))
      }
    })

  val officeOfDestination: ReaderT[ParseHandler, NodeSeq, DestinationOffice] =
    ReaderT[ParseHandler, NodeSeq, DestinationOffice](xml => {
      (xml \ "CUSOFFDESEST" \ "RefNumEST1").text match {
        case destination if destination.isEmpty =>Left(DestinationEmpty("Destination Empty"))
        case destination => Right(DestinationOffice(destination))
      }
    })
}
