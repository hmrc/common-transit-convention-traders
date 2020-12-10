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

import com.google.inject.Inject
import models.ParseError.{AmountWithoutCurrency, GuaranteeNotFound}
import models.{ChangeGuaranteeInstruction, Guarantee, NoChangeGuaranteeInstruction, NoChangeInstruction, ParseError, SpecialMention, SpecialMentionGuarantee, SpecialMentionOther, TransformInstruction}

class InstructionBuilder @Inject()(guaranteeInstructionBuilder: GuaranteeInstructionBuilder) {


  def buildInstruction(sm: SpecialMention, guarantees: Seq[Guarantee]): Either[ParseError, TransformInstruction] = {
    sm match {
      case m: SpecialMentionOther => Right(NoChangeInstruction(m.xml))
      case m: SpecialMentionGuarantee => pair(m, guarantees) match {
        case None => Left(GuaranteeNotFound("Guarantee not found"))
        case Some((s, g)) => guaranteeInstructionBuilder.buildInstructionFromGuarantee(g, s) match {
          case Left(error) => Left(error)
          case Right(instruction) => Right(instruction)
        }
      }
    }
  }

  def pair(mention: SpecialMentionGuarantee, guarantees: Seq[Guarantee]): Option[(SpecialMentionGuarantee, Guarantee)] =
    guarantees.filter(g => mention.additionalInfo.endsWith(g.gReference)).headOption match {
      case Some(guarantee) => Some((mention, guarantee))
      case None => None
    }



}

class GuaranteeInstructionBuilder() {

  def buildInstructionFromGuarantee(g: Guarantee, sm: SpecialMentionGuarantee): Either[ParseError, TransformInstruction] = {
    if(!Guarantee.referenceTypes.contains(g.gType)) {
      Right(NoChangeGuaranteeInstruction(sm))
    }
    else
    {
      sm.toDetails(g.gReference).flatMap {
        details => (details.guaranteeAmount, details.currencyCode) match {
          case (Some(_), None) =>
            Left(AmountWithoutCurrency("Parsed Amount value without currency"))
          case (Some(_), Some(_)) =>
            Right(NoChangeGuaranteeInstruction(sm))
          case (None, _) =>{
            val defaultGuaranteeAmount = BigDecimal(10000).setScale(2, BigDecimal.RoundingMode.UNNECESSARY).toString()
            Right(ChangeGuaranteeInstruction(SpecialMentionGuarantee(defaultGuaranteeAmount ++ "EUR" ++ g.gReference)))
          }
        }
      }


    }
  }
}