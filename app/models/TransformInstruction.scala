package models

trait TransformInstruction {
  def reference: String
}

case class NoChangeInstruction(mention: SpecialMentionGuarantee, reference: String)       extends TransformInstruction
case class ChangeInstruction(details: SpecialMentionGuaranteeDetails)  extends TransformInstruction {
  override def reference: String = details.reference
}


case class TransformInstructionSet(guaBlock: GuaBlock, instructions: Seq[TransformInstruction])