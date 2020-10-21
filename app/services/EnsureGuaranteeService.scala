package services

import models.ParseError.{AdditionalInfoMissing, GuaranteeAmountZero, GuaranteeTypeInvalid, NoGuaranteeReferenceNumber, SpecialMentionNotFound}
import models.{ChangeInstruction, GuaBlock, Guarantee, NoChangeInstruction, ParseError, SpecialMention, SpecialMentionGuarantee, SpecialMentionGuaranteeDetails, SpecialMentionOther, TransformInstruction, TransformInstructionSet}
import cats.data.ReaderT
import cats.implicits._

import scala.util.{Success, Try}
import scala.xml.{Elem, Node, NodeSeq}
import scala.xml.transform.{RewriteRule, RuleTransformer}

class EnsureGuaranteeService {

  type ParseHandler[A] = Either[ParseError, A]

  val concernedTypes = Seq[Int](0, 1, 2,4, 9)

  def ensureGuarantee(xml: NodeSeq): Either[ParseError, NodeSeq] =
    createRuleTransformer(xml) match {
      case Right(transformer) => Right(transformer.transform(xml))
      case _ => _
    }


  def createRuleTransformer(xml: NodeSeq): Either[ParseError, RuleTransformer] = {

    parseSpecialMentions(xml).map { s =>
      val others = filterByType[SpecialMention, SpecialMentionOther](s)
      val smGuarantees = filterByType[SpecialMention, SpecialMentionGuarantee](s)

      val instructionSets = guaBlock(xml).map { guaBlocks =>
        guaBlocks.map {
          block =>
            getInstructionSet(block, smGuarantees).map {
              instructionSet => instructionSet
            }
        }
      }

      instructionSets
    }





    new RuleTransformer(new RewriteRule {
      override def transform(node: Node): NodeSeq = {

      }
    })
  }

  def getInstructionSet(guaBlock: GuaBlock, smGuarantees: Seq[SpecialMentionGuarantee]): Either[ParseError, TransformInstructionSet] = {
    liftParseError(guaBlock.guarantees.map {
      guarantee =>
        pair(guarantee, smGuarantees) match {
          case Left(error) => Left(error)
          case Right((g, s)) => buildInstruction(g, s) match {
            case Left(error) => Left(error)
            case Right(instruction) => Right(instruction)
          }
        }
    }) match {
      case Left(error) => Left(error)
      case Right(instructions) => Right(TransformInstructionSet(guaBlock, instructions))
    }
  }

  def buildInstruction(g: Guarantee, sm: SpecialMentionGuarantee): Either[ParseError, TransformInstruction] = {
    if(concernedTypes.contains(g.gType)) {
      Right(NoChangeInstruction(sm, g.gReference))
    }
    else
    {
      checkDetails(g, sm).map {
        details => ChangeInstruction(details)
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

  def pair(guarantee: Guarantee, mentions: Seq[SpecialMentionGuarantee]): Either[ParseError, (Guarantee, SpecialMentionGuarantee)] = {
    mentions.filter(s => s.additionalInfo.endsWith(guarantee.gReference)).headOption match {
      case Some(s) => Right((guarantee, s))
      case None => Left(SpecialMentionNotFound(s"No special mention for guarantee ref: ${guarantee.gReference}"))
    }
  }

  def parseGuarantees(xml: NodeSeq): ParseHandler[Seq[Guarantee]] = {

    val guaranteeEithers: Seq[Either[ParseError, Guarantee]] =
      (xml \ "GUAGUA").map {
        node => guarantee(node) match {
          case Left(e) => Left(e)
          case Right(g) => Right(g)
        }
      }

    guaranteeEithers.filterNot(x => x.isRight).headOption match {
      case Some(error) => Left(error.left.get)
      case None => Right(guaranteeEithers.map { g => g.right.get } )
    }
  }

  def parseSpecialMentions(xml: NodeSeq): ParseHandler[Seq[SpecialMention]] =
    liftParseError((xml \ "GOOITEGDS" \ "SPEMENMT2").map {
      node => specialMention(node) match {
        case Left(e) => Left(e)
        case Right(s) => Right(s)
      }
    })

  def filterByType[A, B](input: Seq[A]) : Seq[B] =
    input.filter(x => x.isInstanceOf[B]).map { y => y.asInstanceOf[B] }

  def liftParseError[A](input: Seq[Either[ParseError, A]]): ParseHandler[Seq[A]] =
    input.filterNot(i => i.isRight).headOption match {
      case Some(error) => Left(error.left.get)
      case None => Right(input.map {
        x => x.right.get
      })
    }

  val guaBlock: ReaderT[ParseHandler, NodeSeq, Seq[GuaBlock]] =
    ReaderT[ParseHandler, NodeSeq, Seq[GuaBlock]](xml => {
      liftParseError((xml \ "GuaTypGUA1" ).map {
        node => {
          parseGuarantees(node) match {
            case Left(error) => Left(error)
            case Right(guarantees) => Right(GuaBlock(guarantees))
          }
        }
      })
    })

  val guarantee: ReaderT[ParseHandler, Node, Guarantee] =
    ReaderT[ParseHandler, Node, Guarantee](xml => {
      (xml \ "GuaTypGUA1").text match {
        case gType if !gType.isEmpty && Try(gType.toInt).toOption.isEmpty => Left(GuaranteeTypeInvalid("GuaTypGUA1 was invalid"))
        case gType if !gType.isEmpty && Try(gType.toInt).toOption.isDefined => {
          (xml \ "GUAREFREF" \ "GuaRefNumGRNREF1").text match {
            case gReference if !gReference.isEmpty => Right(Guarantee(gType.toInt, gReference))
            case _ => Left(NoGuaranteeReferenceNumber("GuaRefNumGRNREF1 was empty"))
          }}}})

  val specialMention: ReaderT[ParseHandler, Node, SpecialMention] =
    ReaderT[ParseHandler, Node, SpecialMention](xml => {
      (xml \ "AddInfMT21").text match {
        case additionalInfo if additionalInfo.isEmpty => Left(AdditionalInfoMissing("AddInfMT21 field is missing"))
        case additionalInfo => (xml \ "AddInfCodMT23").text match {
          case "CAL" => Right(SpecialMentionGuarantee(additionalInfo))
          case _ => Right(SpecialMentionOther(xml))
        }
      }})

}
