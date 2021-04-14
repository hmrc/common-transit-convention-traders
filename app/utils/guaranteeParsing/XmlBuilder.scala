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

import javax.inject.Inject
import models.{AddSpecialMentionInstruction, ChangeGuaranteeInstruction, NoChangeGuaranteeInstruction, NoChangeInstruction, SpecialMentionGuarantee, TransformInstruction}

import scala.xml.Node

class XmlBuilder @Inject()() {

  def buildFromInstruction(instruction: TransformInstruction): Node = {
    instruction match {
      case e: NoChangeInstruction => e.xml.head
      case e: NoChangeGuaranteeInstruction => buildGuaranteeXml(e.mention)
      case e: ChangeGuaranteeInstruction => buildGuaranteeXml(e.mention)
      case e: AddSpecialMentionInstruction => buildGuaranteeXml(e.mention)
    }
  }

  def buildGuaranteeXml(mention: SpecialMentionGuarantee) = {
    val addInfMT21LNGText = (mention.xml \ "AddInfMT21LNG").text
    val expFroECMT24Text = (mention.xml \ "ExpFroECMT24").text
    val expFroCouMT25Text = (mention.xml \ "ExpFroCouMT25").text
    <SPEMENMT2><AddInfMT21>{mention.additionalInfo}</AddInfMT21>{if(!addInfMT21LNGText.isEmpty) <AddInfMT21LNG>{addInfMT21LNGText}</AddInfMT21LNG>}<AddInfCodMT23>CAL</AddInfCodMT23>{if(!expFroECMT24Text.isEmpty) <ExpFroECMT24>{expFroECMT24Text}</ExpFroECMT24>}{if(!expFroCouMT25Text.isEmpty) <ExpFroCouMT25>{expFroCouMT25Text}</ExpFroCouMT25>}</SPEMENMT2>

  }

}
