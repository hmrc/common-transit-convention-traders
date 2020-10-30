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

import cats.data.ReaderT
import models.ParseError._
import models._

import scala.util.{Failure, Success, Try}
import scala.xml.{Node, NodeSeq}

class GuaranteeXmlReaders extends ParseHandling {

  def parseGuarantees(xml: NodeSeq): ParseHandler[Seq[Guarantee]] = {

    val guaranteeEithers: Seq[Either[ParseError, Guarantee]] =
      (xml \ "GUAGUA").map {
        node => guarantee(node) match {
          case Left(e) => Left(e)
          case Right(g) => Right(g)
        }
      }

    ParseError.liftParseError(guaranteeEithers)
  }

  def parseSpecialMentions(xml: NodeSeq): ParseHandler[Seq[SpecialMention]] = {
    ParseError.liftParseError((xml \ "SPEMENMT2").map {
      node =>
        specialMention(node) match {
          case Left(e) => Left(e)
          case Right(s) => Right(s)
        }
    })
  }

  val gooBlock: ReaderT[ParseHandler, NodeSeq, Seq[GooBlock]] =
    ReaderT[ParseHandler, NodeSeq, Seq[GooBlock]](xml => {
      ParseError.liftParseError((xml \ "GOOITEGDS" ).map {
        node => {
          val itemNumberNode = (node \ "IteNumGDS7")
          if(!itemNumberNode.isEmpty)
          {
            val itemNumberString = itemNumberNode.text
            if(!itemNumberString.isEmpty)
            {
              Try(itemNumberString.toInt) match {
                case Failure(_) => Left(InvalidItemNumber("Invalid Item Number"))
                case Success(itemNumber) =>
                  parseSpecialMentions(node) match {
                    case Left(error) => Left(error)
                    case Right(mentions) => Right(GooBlock(itemNumber, mentions))
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
        case gType if !gType.isEmpty && Try(gType.toInt).toOption.isEmpty => Left(GuaranteeTypeInvalid("GuaTypGUA1 was invalid"))
        case gType if !gType.isEmpty && Try(gType.toInt).toOption.isDefined => {
          (xml \ "GUAREFREF" \ "GuaRefNumGRNREF1").text match {
            case gReference if !gReference.isEmpty => Right(Guarantee(gType.toInt, gReference))
            case _ => Left(NoGuaranteeReferenceNumber("GuaRefNumGRNREF1 was empty"))
          }}}})
}
