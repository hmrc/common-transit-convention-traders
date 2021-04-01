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

package services

import models.{ChangeGuaranteeInstruction, GOOITEGDSNode, Guarantee, NoChangeGuaranteeInstruction, NoChangeInstruction, ParseError, ParseHandling, SpecialMentionGuarantee, TransformInstruction, TransformInstructionSet}
import com.google.inject.Inject
import models.ParseError.UnpairedGuarantees
import utils.guaranteeParsing.{GuaranteeXmlReaders, InstructionBuilder}
import utils.guaranteeParsing.RouteChecker

import scala.xml.{Elem, Node, NodeSeq}
import scala.xml.transform.{RewriteRule, RuleTransformer}

class EnsureGuaranteeService @Inject()(xmlReaders: GuaranteeXmlReaders, instructionBuilder: InstructionBuilder, routeChecker: RouteChecker) extends ParseHandling {

  def ensureGuarantee(xml: NodeSeq): ParseHandler[NodeSeq] =
    routeChecker.gbOnlyCheck(xml) match {
      case Left(error) => Left(error)
      case Right(gbOnly) if gbOnly => Right(xml)
      case Right(_) =>
        //TODO: A performance improvement could be to get the unpaired guarantees as an extra value here.
        findUnpairedGuarantees(xml).flatMap {
          unpaired =>
            if(unpaired.length > 0)
              Left(UnpairedGuarantees(s"${unpaired.length} guarantees found without special mentions"))
            else parseInstructionSets(xml) match {
              case Left(error) => Left(error)
              case Right(instructionSets) =>
                Right(updateXml(xml, instructionSets))
            }
        }
    }

  def findUnpairedGuarantees(xml: NodeSeq): Either[ParseError,Seq[Guarantee]] = {
    xmlReaders.parseGuarantees(xml) match {
      case Left(error) => Left(error)
      case Right(guarantees) =>

        val referenceGuarantees = guarantees.filter(g => Guarantee.referenceTypes.contains(g.gType))

        xmlReaders.gOOITEGDSNode(xml).map {
          goodsNodes => goodsNodes.map {
            goodsItem => goodsItem.specialMentions
          }
        } match {
          case Left(error) => Left(error)
          case Right(seqseq) =>
            val flattened = seqseq.flatMap(_.toList)
            val mentionedOptGuarantees = flattened.filter(sm => sm.isInstanceOf[SpecialMentionGuarantee]).map {
              m => val pairs = instructionBuilder.pair(m.asInstanceOf[SpecialMentionGuarantee], referenceGuarantees)
                println(m.asInstanceOf[SpecialMentionGuarantee])
                println(referenceGuarantees)
                println(pairs)
                pairs.map {
                pair =>
                  println(pair)
                  pair._2
              }
            }
            val mentionedGuarantees = mentionedOptGuarantees.filter(g => g.isDefined).map(g => g.get)
            Right(referenceGuarantees.diff(mentionedGuarantees))
        }
    }
  }

  def parseInstructionSets(xml: NodeSeq): ParseHandler[Seq[TransformInstructionSet]] = {
    xmlReaders.parseGuarantees(xml) match {
      case Left(error) => Left(error)
      case Right(guarantees) =>
        xmlReaders.gOOITEGDSNode(xml) match {
          case Left(error) => Left(error)
          case Right(gooBlocks) =>
            ParseError.sequenceErrors(gooBlocks.map {
              block =>
                getInstructionSet(block, guarantees).map {
                  instructionSet => instructionSet
                }
            })
        }
    }
  }

  def updateXml(originalXml: NodeSeq, instructionSets: Seq[TransformInstructionSet]): NodeSeq = {
    new RuleTransformer(new RewriteRule {
      override def transform(node: Node): NodeSeq = {
        node match {
          case e: Elem if e.label == "GOOITEGDS" => {
            xmlReaders.gOOITEGDSNodeFromNode(e) match {
              case Left(_) => throw new Exception("Unable to parse GOOITEGDSNode on update")
              case Right(currentBlock) => {
                val instructionSet = instructionSets.filter(set => set.gooNode.itemNumber == currentBlock.itemNumber).head
                val mentionInstructionPairs = e.child.filter(cNode => cNode.label == "SPEMENMT2").zip(instructionSet.instructions)
                val newChildren = e.child.map {
                  cNode => if(cNode.label == "SPEMENMT2") {
                    mentionInstructionPairs.find(pair => pair._1 == cNode) match {
                      case None => throw new Exception("Unable to match instruction with mention on update")
                      case Some(matchedPair) => {
                        val instruction = matchedPair._2
                        buildFromInstruction(instruction)
                      }
                    }
                  } else cNode
                }
                e.copy(child = newChildren)
              }
            }
          }
          case _ => node
        }
      }
    }).transform(originalXml)
  }

  def buildFromInstruction(instruction: TransformInstruction): Node = {
    instruction match {
      case e: NoChangeInstruction => e.xml.head
      case e: NoChangeGuaranteeInstruction => buildGuaranteeXml(e.mention)
      case e: ChangeGuaranteeInstruction => buildGuaranteeXml(e.mention)
    }
  }

  def buildGuaranteeXml(mention: SpecialMentionGuarantee) =
    <SPEMENMT2><AddInfMT21>{mention.additionalInfo}</AddInfMT21><AddInfCodMT23>CAL</AddInfCodMT23></SPEMENMT2>

  def getInstructionSet(gooNode: GOOITEGDSNode, guarantees: Seq[Guarantee]): Either[ParseError, TransformInstructionSet] = {
    ParseError.sequenceErrors(gooNode.specialMentions.map {
      mention =>
        instructionBuilder.buildInstruction(mention, guarantees)
    }).map {
      instructions => TransformInstructionSet(gooNode, instructions)
    }
  }










}
