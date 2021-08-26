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

package models

import scala.xml.NodeSeq

sealed trait TransformInstruction

final case class NoChangeInstruction(xml: NodeSeq)                              extends TransformInstruction
final case class NoChangeGuaranteeInstruction(mention: SpecialMentionGuarantee) extends TransformInstruction
final case class ChangeGuaranteeInstruction(mention: SpecialMentionGuarantee)   extends TransformInstruction
final case class AddSpecialMentionInstruction(mention: SpecialMentionGuarantee) extends TransformInstruction

final case class TransformInstructionSet(gooNode: GOOITEGDSNode, instructions: Seq[TransformInstruction])
