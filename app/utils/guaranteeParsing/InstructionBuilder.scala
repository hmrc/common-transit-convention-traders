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
import config.DefaultGuaranteeConfig
import models.AddSpecialMentionInstruction
import models.ChangeGuaranteeInstruction
import models.GOOITEGDSNode
import models.Guarantee
import models.NoChangeGuaranteeInstruction
import models.NoChangeInstruction
import models.ParseError
import models.ParseError.AmountWithoutCurrency
import models.ParseError.InvalidAmount
import models.SpecialMentionGuarantee
import models.SpecialMentionOther
import models.TransformInstruction
import models.TransformInstructionSet

import javax.inject.Inject

class InstructionBuilder @Inject() (guaranteeInstructionBuilder: GuaranteeInstructionBuilder) {

  def buildInstructionSet(gooNode: GOOITEGDSNode, guarantees: Seq[Guarantee]): Either[ParseError, TransformInstructionSet] = {
    val defaultingGuarantees = guarantees.filter(_.isDefaulting)

    val specialMentionGuarantees = gooNode.specialMentions.collect {
      case smg: SpecialMentionGuarantee => smg
    }

    val mentionedGuarantees = defaultingGuarantees.map {
      g => findMatchingMention(g, specialMentionGuarantees)
    }

    val preserveInstructions = gooNode.specialMentions.collect {
      case smo: SpecialMentionOther =>
        NoChangeInstruction(smo.xml)
    }

    mentionedGuarantees.toList
      .traverse {
        case (m, g) =>
          guaranteeInstructionBuilder.buildInstructionFromGuarantee(g, m)
      }
      .map {
        instructions =>
          TransformInstructionSet(gooNode, instructions ++ preserveInstructions)
      }
  }

  def findMatchingMention(guarantee: Guarantee, specialMentionGuarantees: Seq[SpecialMentionGuarantee]): (Option[SpecialMentionGuarantee], Guarantee) = {
    val matchingMention = specialMentionGuarantees.find(
      sm => sm.additionalInfo.endsWith(guarantee.gReference)
    )
    (matchingMention, guarantee)
  }
}

class GuaranteeInstructionBuilder @Inject() (defaultGuaranteeConfig: DefaultGuaranteeConfig) {

  def buildInstructionFromGuarantee(g: Guarantee, osm: Option[SpecialMentionGuarantee]): Either[ParseError, TransformInstruction] = {
    val defaultGuaranteeAmount   = BigDecimal(defaultGuaranteeConfig.amount).setScale(2, BigDecimal.RoundingMode.UNNECESSARY).toString()
    val defaultGuaranteeCurrency = defaultGuaranteeConfig.currency
    val defaultGuarantee         = SpecialMentionGuarantee(defaultGuaranteeAmount ++ defaultGuaranteeCurrency ++ g.gReference, Nil)
    osm match {
      case Some(sm) =>
        if (!Guarantee.referenceTypes.contains(g.gType)) {
          Right(NoChangeGuaranteeInstruction(sm))
        } else {
          sm.toDetails(g.gReference).flatMap {
            details =>
              (details.guaranteeAmount, details.currencyCode) match {
                case (Some(_), None) =>
                  Left(AmountWithoutCurrency("Parsed Amount value without currency"))
                case (Some(amount), Some(_)) if amount > 0 =>
                  Right(NoChangeGuaranteeInstruction(sm))
                case (Some(_), Some(_)) =>
                  Left(InvalidAmount("Amount cannot be equal to or less than 0"))
                case (None, _) =>
                  Right(ChangeGuaranteeInstruction(SpecialMentionGuarantee(defaultGuaranteeAmount ++ defaultGuaranteeCurrency ++ g.gReference, sm.xml)))
              }
          }
        }
      case None =>
        Right(AddSpecialMentionInstruction(defaultGuarantee))
    }

  }
}
