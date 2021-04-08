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

import models.{AddSpecialMentionInstruction, ChangeGuaranteeInstruction, GOOITEGDSNode, Guarantee, NoChangeGuaranteeInstruction, NoChangeInstruction, ParseError, ParseHandling, SpecialMentionGuarantee, SpecialMentionOther, TransformInstruction, TransformInstructionSet}
import com.google.inject.Inject
import models.ParseError.{GuaranteeNotFound, UnpairedGuarantees}
import utils.guaranteeParsing.{GuaranteeInstructionBuilder, GuaranteeXmlReaders, InstructionBuilder, RouteChecker, XmlBuilder}

import scala.xml.{Elem, Node, NodeSeq}
import scala.xml.transform.{RewriteRule, RuleTransformer}

class EnsureGuaranteeService @Inject()(xmlReaders: GuaranteeXmlReaders, instructionBuilder: InstructionBuilder, routeChecker: RouteChecker, guaranteeInstructionBuilder: GuaranteeInstructionBuilder, xmlBuilder: XmlBuilder) extends ParseHandling {

  val specialMentionParents = Seq(
    "GooDesGDS23LNG",
    "GroMasGDS46",
    "NetMasGDS48",
    "CouOfDisGDS58",
    "CouOfDesGDS59",
    "MetOfPayGDI12",
    "ComRefNumGIM1",
    "UNDanGooCodGDI1",
    "PREADMREFAR2",
    "PRODOCDC2",
    "SPEMENMT2")

  def ensureGuarantee(xml: NodeSeq): ParseHandler[NodeSeq] =
  routeChecker.gbOnlyCheck(xml) match {
    case Left(error) => Left(error)
    case Right(gbOnly) if gbOnly => Right(xml)
    case Right(_) =>
      parseInstructionSets(xml) match {
        case Left(error) => Left(error)
        case Right(instructionSets) =>
          Right(updateXml(xml, instructionSets))
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
                instructionBuilder.buildInstructionSet(block, guarantees)
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
                val modifyInstructions = instructionSet.instructions.filterNot(ti => ti.isInstanceOf[AddSpecialMentionInstruction])
                val modifyInstructionPairs = e.child.filter(cNode => cNode.label == "SPEMENMT2").zip(modifyInstructions)
                val modifiedChildren = e.child.map {
                  cNode => if(cNode.label == "SPEMENMT2") {
                    modifyInstructionPairs.find(pair => pair._1 == cNode) match {
                      case None => throw new Exception("Unable to match instruction with mention on update")
                      case Some(matchedPair) => {
                        val instruction = matchedPair._2
                        xmlBuilder.buildFromInstruction(instruction)
                      }
                    }
                  } else cNode
                }
                val last = modifiedChildren.filter(cNode => specialMentionParents.contains(cNode.label)).last
                val addSpecialMentionInstructions = instructionSet.instructions.filter(ti => ti.isInstanceOf[AddSpecialMentionInstruction])
                val toAppendXml = if(currentBlock.itemNumber == 1) {
                  addSpecialMentionInstructions.map {
                    sm => xmlBuilder.buildFromInstruction(sm)
                  }
                } else Nil

                val division = modifiedChildren.splitAt(modifiedChildren.indexOf(last) +1)
                val finalXml = (division._1 ++ toAppendXml ++ division._2)
                e.copy(child = finalXml)
              }
            }
          }
          case _ => node
        }
      }
    }).transform(originalXml)
  }
}
