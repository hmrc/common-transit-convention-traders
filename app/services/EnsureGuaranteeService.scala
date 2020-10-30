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
import models.{ChangeGuaranteeInstruction, GooBlock, Guarantee, NoChangeGuaranteeInstruction, NoChangeInstruction, ParseError, ParseHandling, SpecialMention, SpecialMentionGuarantee, SpecialMentionGuaranteeDetails, SpecialMentionOther, TransformInstruction, TransformInstructionSet}
import cats.data.ReaderT
import cats.implicits._
import com.google.inject.Inject
import utils.guaranteeParsing.{GuaranteeXmlReaders, InstructionBuilder}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node, NodeSeq}
import scala.xml.transform.{RewriteRule, RuleTransformer}

class EnsureGuaranteeService @Inject()(xmlReaders: GuaranteeXmlReaders, instructionBuilder: InstructionBuilder) extends ParseHandling {


  def ensureGuarantee(xml: NodeSeq): ParseHandler[NodeSeq] =
    parseInstructionSets(xml) match {
      case Left(error) => Left(error)
      case Right(instructionSets) =>
        Right(updateXml(prunedXml(xml), instructionSets))
    }

  def parseInstructionSets(xml: NodeSeq): ParseHandler[Seq[TransformInstructionSet]] = {
    println("parseGuarantees"); xmlReaders.parseGuarantees(xml) match {
      case Left(error) => Left(error)
      case Right(guarantees) =>
        println("gooBlock"); xmlReaders.gooBlock(xml) match {
          case Left(error) => Left(error)
          case Right(gooBlocks) =>
            ParseError.liftParseError(gooBlocks.map {
              block =>
                println("getInstructionSet"); getInstructionSet(block, guarantees).map {
                  instructionSet => instructionSet
                }
            })
        }
    }
  }

  def updateXml(prunedXml: NodeSeq, instructionSets: Seq[TransformInstructionSet]): NodeSeq = {
    new RuleTransformer(new RewriteRule {
      override def transform(node: Node): NodeSeq = {
        node match {
          case e: Elem if e.label == "GOOITEGDS" => {
            xmlReaders.gooBlock(e) match {
              case Left(_) => throw new Exception()
              case Right(blocks) => {
                val currentBlock = blocks.head
                val instructionSet = instructionSets.filter(set => set.gooBlock.itemNumber == currentBlock.itemNumber).head
                val newXml = buildBlockBody(instructionSet)
                val newBody = e.child.last ++ newXml
                e.copy(child = newBody)
              }
            }
          }
          case _ => node
        }
      }
    }).transform(prunedXml)
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


  private def mergeNewXml(xmls: Seq[NodeSeq]): NodeSeq = {
    @tailrec
    def mergeAggregator(xmls: Seq[NodeSeq], accum: NodeSeq): NodeSeq = {
      xmls match {
        case Nil => accum
        case x :: tail => mergeAggregator(tail, accum ++ x)
      }
    }
    mergeAggregator(xmls, NodeSeq.Empty)
  }

  def buildBlockBody(instructionSet: TransformInstructionSet): NodeSeq = {
    mergeNewXml(instructionSet.instructions.map {
      instruction => instruction match {
        case e: NoChangeInstruction => e.xml
        case e: NoChangeGuaranteeInstruction => buildGuaranteeXml(e.mention)
        case e: ChangeGuaranteeInstruction => buildGuaranteeXml(e.details.toSimple)
      }
    })
  }

  def buildGuaranteeXml(mention: SpecialMentionGuarantee) =
    <SPEMENMT2><AddInfCodMT21>{mention.additionalInfo}</AddInfCodMT21><AddInfCodMT23>CAL</AddInfCodMT23></SPEMENMT2>

  def getInstructionSet(gooBlock: GooBlock, guarantees: Seq[Guarantee]): Either[ParseError, TransformInstructionSet] = {
    ParseError.liftParseError(gooBlock.specialMentions.map {
      mention =>
        instructionBuilder.buildInstruction(mention, guarantees)
    }).map {
      instructions => println("build TIS"); TransformInstructionSet(gooBlock, instructions)
    }
  }










}
