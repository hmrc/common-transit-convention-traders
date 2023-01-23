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

import cats.data.ReaderT
import models.ParseError._
import models._

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.xml.Node
import scala.xml.NodeSeq

class GuaranteeXmlReaders extends ParseHandling {

  def parseGuarantees(xml: NodeSeq): ParseHandler[Seq[Guarantee]] = {

    val guaranteeSequences: Seq[Either[ParseError, Seq[Guarantee]]] =
      (xml \ "GUAGUA").map {
        node =>
          guaranteeSet(node)
      }

    ParseError.sequenceErrors(guaranteeSequences) match {
      case Left(error) => Left(error)
      case Right(ss)   => Right(ss.flatten)
    }
  }

  def parseSpecialMentions(xml: NodeSeq): ParseHandler[Seq[SpecialMention]] =
    ParseError.sequenceErrors((xml \ "SPEMENMT2").map {
      node =>
        specialMention(node)
    })

  val gOOITEGDSNode: ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]] =
    ReaderT[ParseHandler, NodeSeq, Seq[GOOITEGDSNode]] {
      xml =>
        ParseError.sequenceErrors((xml \ "GOOITEGDS").map {
          node =>
            val itemNumberNode = node \ "IteNumGDS7"
            if (itemNumberNode.nonEmpty) {
              val itemNumberString = itemNumberNode.text
              if (itemNumberString.nonEmpty) {
                Try(itemNumberString.toInt) match {
                  case Failure(_) => Left(InvalidItemNumber("Invalid Item Number"))
                  case Success(itemNumber) =>
                    parseSpecialMentions(node) match {
                      case Left(error)     => Left(error)
                      case Right(mentions) => Right(GOOITEGDSNode(itemNumber, mentions))
                    }
                }
              } else {
                Left(MissingItemNumber("Missing Item Number"))
              }
            } else {
              Left(MissingItemNumber("Missing Item Number"))
            }

        })
    }

  val gOOITEGDSNodeFromNode: ReaderT[ParseHandler, Node, GOOITEGDSNode] =
    ReaderT[ParseHandler, Node, GOOITEGDSNode] {
      node =>
        val itemNumberNode = node \ "IteNumGDS7"
        if (itemNumberNode.nonEmpty) {
          val itemNumberString = itemNumberNode.text
          if (itemNumberString.nonEmpty) {
            Try(itemNumberString.toInt) match {
              case Failure(_) => Left(InvalidItemNumber("Invalid Item Number"))
              case Success(itemNumber) =>
                parseSpecialMentions(node) match {
                  case Left(error)     => Left(error)
                  case Right(mentions) => Right(GOOITEGDSNode(itemNumber, mentions))
                }
            }
          } else {
            Left(MissingItemNumber("Missing Item Number"))
          }
        } else {
          Left(MissingItemNumber("Missing Item Number"))
        }
    }

  val specialMention: ReaderT[ParseHandler, Node, SpecialMention] =
    ReaderT[ParseHandler, Node, SpecialMention] {
      xml =>
        val AddInfMT21    = xml \ "AddInfMT21"
        val AddInfCodMT23 = xml \ "AddInfCodMT23"

        (AddInfMT21.text.isEmpty, AddInfCodMT23.text.isEmpty) match {
          case (false, false) if AddInfCodMT23.text.equals("CAL") => Right(SpecialMentionGuarantee(AddInfMT21.text, xml))
          case _                                                  => Right(SpecialMentionOther(xml))
        }
    }

  val guaranteeSet: ReaderT[ParseHandler, Node, Seq[Guarantee]] =
    ReaderT[ParseHandler, Node, Seq[Guarantee]] {
      xml =>
        def internal(key: String, gChar: Char, error: => ParseError)(reference: Node): ParseHandler[Guarantee] =
          (reference \ key).text match {
            case g if !g.isEmpty => Right(Guarantee(gChar, g))
            case _               => Left(error)
          }

        (xml \ "GuaTypGUA1").text match {
          case gType if gType.isEmpty                              => Left(GuaranteeTypeInvalid("GuaTypGUA1 was invalid"))
          case gType if gType.length > 1                           => Left(GuaranteeTypeTooLong("GuaTypGUA1 was too long"))
          case gType if !Guarantee.validTypes.contains(gType.head) => Left(GuaranteeTypeInvalid("GuaTypGUA1 was not a valid type"))
          case gType if Guarantee.validTypes.contains(gType.head) =>
            val gChar               = gType.head
            val guaranteeReferences = (xml \ "GUAREFREF").theSeq.toSeq
            val gParse: Seq[ParseHandler[Guarantee]] = if (Guarantee.isOther(gChar)) {
              guaranteeReferences.map(internal("OthGuaRefREF4", gChar, NoOtherGuaranteeField("OthGuaRefREF4 was empty")))
            } else {
              guaranteeReferences.map(internal("GuaRefNumGRNREF1", gChar, NoGuaranteeReferenceNumber("GuaRefNumGRNREF1 was empty")))
            }
            ParseError.sequenceErrors(gParse)
        }
    }

  val officeOfDeparture: ReaderT[ParseHandler, NodeSeq, DepartureOffice] =
    ReaderT[ParseHandler, NodeSeq, DepartureOffice] {
      xml =>
        (xml \ "CUSOFFDEPEPT" \ "RefNumEPT1").text match {
          case departure if departure.isEmpty => Left(DepartureEmpty("Departure Empty"))
          case departure                      => Right(DepartureOffice(departure))
        }
    }

  val officeOfDestination: ReaderT[ParseHandler, NodeSeq, DestinationOffice] =
    ReaderT[ParseHandler, NodeSeq, DestinationOffice] {
      xml =>
        (xml \ "CUSOFFDESEST" \ "RefNumEST1").text match {
          case destination if destination.isEmpty => Left(DestinationEmpty("Destination Empty"))
          case destination                        => Right(DestinationOffice(destination))
        }
    }
}
