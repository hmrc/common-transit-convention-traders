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

import models.ParseError.{AdditionalInfoCodeMissing, AdditionalInfoMissing, GuaranteeAmountZero, GuaranteeNotFound, GuaranteeTypeInvalid, InvalidItemNumber, MissingItemNumber, NoGuaranteeReferenceNumber, SpecialMentionNotFound}
import models.{ChangeGuaranteeInstruction, GooBlock, Guarantee, NoChangeGuaranteeInstruction, NoChangeInstruction, ParseError, SpecialMention, SpecialMentionGuarantee, SpecialMentionGuaranteeDetails, SpecialMentionOther, TransformInstruction, TransformInstructionSet}
import cats.data.ReaderT
import cats.implicits._

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node, NodeSeq}
import scala.xml.transform.{RewriteRule, RuleTransformer}

class EnsureGuaranteeService {

  type ParseHandler[A] = Either[ParseError, A]

  val concernedTypes = Seq[Int](0, 1, 2,4, 9)

  def ensureGuarantee(xml: NodeSeq): ParseHandler[NodeSeq] =
    parseInstructionSets(xml) match {
      case Left(error) => Left(error)
      case Right(instructionSets) =>
        Right(updateXml(prunedXml(xml), instructionSets))
    }

  def parseInstructionSets(xml: NodeSeq): ParseHandler[Seq[TransformInstructionSet]] =
    parseGuarantees(xml) match {
      case Left(error) => Left(error)
      case Right(guarantees) =>
        gooBlock(xml) match {
          case Left(error) => Left(error)
          case Right(gooBlocks) =>
            liftParseError(gooBlocks.map {
              block =>
                getInstructionSet(block, guarantees).map {
                  instructionSet => instructionSet
                }
            })
        }
    }

  def prunedXml(xml: NodeSeq): NodeSeq = {
    new RuleTransformer(new RewriteRule {
      override def transform(node: Node): NodeSeq = {
        node match {
          case e: Elem if e.label == "SPEMENMT2" => NodeSeq.Empty
          case _ => node
        }
      }
    }).transform(xml)
  }

  def updateXml(prunedXml: NodeSeq, instructionSets: Seq[TransformInstructionSet]): NodeSeq = {
    new RuleTransformer(new RewriteRule {
      override def transform(node: Node): NodeSeq = {
        node match {
          case e: Elem if e.label == "GOOITEGDS" => {
            gooBlock(e) match {
              case Left(_) => throw new Exception()
              case Right(blocks) => {
                val currentBlock = blocks.head
                val instructionSet = instructionSets.filter(set => set.gooBlock.itemNumber == currentBlock.itemNumber).head
                val newXml = buildBlockXml(instructionSet)
                e.child.last ++ newXml
              }
            }
          }
          case _ => node
        }
      }
    }).transform(prunedXml)
  }


  private def mergeNewXml(xmls: Seq[NodeSeq]): NodeSeq = {
    @tailrec
    def mergeAggregator(xmls: Seq[NodeSeq], accum: NodeSeq): NodeSeq = {
      xmls match {
        case NodeSeq.Empty => accum
        case x :: tail => mergeAggregator(tail, accum ++ x)
      }
    }
    mergeAggregator(xmls, NodeSeq.Empty)
  }

  def buildBlockXml(instructionSet: TransformInstructionSet): NodeSeq = {
    mergeNewXml(instructionSet.instructions.map {
      instruction => instruction match {
        case e: NoChangeInstruction => e.xml
        case e: NoChangeGuaranteeInstruction => buildGuaranteeXml(e.mention)
        case e: ChangeGuaranteeInstruction => buildGuaranteeXml(e.details.toSimple)
      }
    })
  }

  def buildGuaranteeXml(mention: SpecialMentionGuarantee) =
    <SPEMENMT2>
      <AddInfCodMT21>{mention.additionalInfo}</AddInfCodMT21>
      <AddInfCodMT23>CAL</AddInfCodMT23>
    </SPEMENMT2>

  def getInstructionSet(gooBlock: GooBlock, guarantees: Seq[Guarantee]): Either[ParseError, TransformInstructionSet] = {
    liftParseError(gooBlock.specialMentions.map {
      mention =>
        mention match {
          case m: SpecialMentionOther => Right(NoChangeInstruction(m.xml))
          case m: SpecialMentionGuarantee => pair(m, guarantees) match {
            case None => Left(GuaranteeNotFound("Guarantee not found"))
            case Some((s, g)) => buildInstruction(g, s) match {
              case Left(error) => Left(error)
              case Right(instruction) => Right(instruction)
            }
          }
        }
    }).map {
      instructions => TransformInstructionSet(gooBlock, instructions)
    }
  }

  def buildInstruction(g: Guarantee, sm: SpecialMentionGuarantee): Either[ParseError, TransformInstruction] = {
    if(concernedTypes.contains(g.gType)) {
      Right(NoChangeGuaranteeInstruction(sm))
    }
    else
    {
      checkDetails(g, sm).map {
        details => ChangeGuaranteeInstruction(details)
      }
    }
  }

  private def checkDetails(g: Guarantee, s: SpecialMentionGuarantee): Either[ParseError, SpecialMentionGuaranteeDetails] =
    s.toDetails(g.gReference) match {
    case Left(error) => Left(error)
    case Right(details) => details.guaranteeAmount match {
      case None => Right(details.copy(Some(BigDecimal(10000.00)), Some("EUR")))
      case Some(amount) => amount match {
        case a if a.equals(BigDecimal(0)) => Left(GuaranteeAmountZero("GuaranteeAmount cannot be zero"))
        case _ => Right(details)
      }
    }
  }

  def pair(mention: SpecialMentionGuarantee, guarantees: Seq[Guarantee]): Option[(SpecialMentionGuarantee, Guarantee)] =
    guarantees.filter(g => mention.additionalInfo.endsWith(g.gReference)).headOption match {
      case Some(guarantee) => Some(mention, guarantee)
      case None => None
    }

  def parseGuarantees(xml: NodeSeq): ParseHandler[Seq[Guarantee]] = {

    val guaranteeEithers: Seq[Either[ParseError, Guarantee]] =
      (xml \ "GUAGUA").map {
        node => guarantee(node) match {
          case Left(e) => Left(e)
          case Right(g) => Right(g)
        }
      }

    liftParseError(guaranteeEithers)
  }

  def liftParseError[A](input: Seq[Either[ParseError, A]]): ParseHandler[Seq[A]] =
    input.filterNot(i => i.isRight).headOption match {
      case Some(error) => Left(error.left.get)
      case None => Right(input.map {
        x => x.right.get
      })
    }

  val gooBlock: ReaderT[ParseHandler, NodeSeq, Seq[GooBlock]] =
    ReaderT[ParseHandler, NodeSeq, Seq[GooBlock]](xml => {
      liftParseError((xml \ "GOOITEGDS" ).map {
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

  def parseSpecialMentions(xml: NodeSeq): ParseHandler[Seq[SpecialMention]] = {
  liftParseError((xml \ "SPEMENMT2").map {
    node =>
      specialMention(node) match {
        case Left(e) => Left(e)
        case Right(s) => Right(s)
      }
  })
}

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
