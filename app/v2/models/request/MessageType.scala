/*
 * Copyright 2023 HM Revenue & Customs
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

package v2.models.request

import play.api.libs.json.JsError
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import v2.models.AuditType
import v2.models.MovementType

sealed trait MessageType {
  def code: String
  def movementType: MovementType
  def rootNode: String
  def auditType: AuditType
}

sealed abstract class DepartureMessageType(val code: String, val rootNode: String, val auditType: AuditType) extends MessageType {
  val movementType: MovementType = MovementType.Departure
}

sealed abstract class ArrivalMessageType(val code: String, val rootNode: String, val auditType: AuditType) extends MessageType {
  val movementType: MovementType = MovementType.Arrival
}

object MessageType {
  case object AmendmentAcceptance               extends DepartureMessageType("IE004", "CC004C", AuditType.AmendmentAcceptance)
  case object ArrivalNotification               extends ArrivalMessageType("IE007", "CC007C", AuditType.ArrivalNotification)
  case object InvalidationDecision              extends DepartureMessageType("IE009", "CC009C", AuditType.InvalidationDecision)
  case object DeclarationAmendment              extends DepartureMessageType("IE013", "CC013C", AuditType.DeclarationAmendment)
  case object DeclarationInvalidationRequest    extends DepartureMessageType("IE014", "CC014C", AuditType.DeclarationInvalidationRequest)
  case object DeclarationData                   extends DepartureMessageType("IE015", "CC015C", AuditType.DeclarationData)
  case object Discrepancies                     extends DepartureMessageType("IE019", "CC019C", AuditType.Discrepancies)
  case object GoodsReleaseNotification          extends ArrivalMessageType("IE025", "CC025C", AuditType.GoodsReleaseNotification)
  case object MRNAllocated                      extends DepartureMessageType("IE028", "CC028C", AuditType.MRNAllocated)
  case object ReleaseForTransit                 extends DepartureMessageType("IE029", "CC029C", AuditType.ReleaseForTransit)
  case object RecoveryNotification              extends DepartureMessageType("IE035", "CC035C", AuditType.RecoveryNotification)
  case object UnloadingPermission               extends ArrivalMessageType("IE043", "CC043C", AuditType.UnloadingPermission)
  case object UnloadingRemarks                  extends ArrivalMessageType("IE044", "CC044C", AuditType.UnloadingRemarks)
  case object WriteOffNotification              extends DepartureMessageType("IE045", "CC045C", AuditType.WriteOffNotification)
  case object NoReleaseForTransit               extends DepartureMessageType("IE051", "CC051C", AuditType.NoReleaseForTransit)
  case object GuaranteeNotValid                 extends DepartureMessageType("IE055", "CC055C", AuditType.GuaranteeNotValid)
  case object RejectionFromOfficeOfDeparture    extends DepartureMessageType("IE056", "CC056C", AuditType.RejectionFromOfficeOfDeparture)
  case object RejectionFromOfficeOfDestination  extends ArrivalMessageType("IE057", "CC057C", AuditType.RejectionFromOfficeOfDestination)
  case object ControlDecisionNotification       extends DepartureMessageType("IE060", "CC060C", AuditType.ControlDecisionNotification)
  case object ForwardedIncidentNotificationToED extends DepartureMessageType("IE182", "CC182C", AuditType.ForwardedIncidentNotificationToED)

  case object PresentationNotificationForThePreLodgedDeclaration
      extends DepartureMessageType("IE170", "CC170C", AuditType.PresentationNotificationForThePreLodgedDeclaration)
  case object FunctionalNack      extends DepartureMessageType("IE906", "CC906C", AuditType.FunctionalNack)      // TODO: This is also an arrival message
  case object PositiveAcknowledge extends DepartureMessageType("IE928", "CC928C", AuditType.PositiveAcknowledge) // TODO: This is also an arrival message

  val updateMessageTypesSentByDepartureTrader: Seq[MessageType] = Seq(
    DeclarationAmendment,
    DeclarationInvalidationRequest,
    PresentationNotificationForThePreLodgedDeclaration
  )

  val messageTypesSentByDepartureTrader: Seq[MessageType] = DeclarationData +: updateMessageTypesSentByDepartureTrader

  val messageTypesSentToDepartureTrader: Seq[MessageType] = Seq(
    AmendmentAcceptance,
    InvalidationDecision,
    Discrepancies,
    MRNAllocated,
    ReleaseForTransit,
    RecoveryNotification,
    WriteOffNotification,
    NoReleaseForTransit,
    GuaranteeNotValid,
    RejectionFromOfficeOfDeparture,
    ControlDecisionNotification,
    FunctionalNack,
    PositiveAcknowledge,
    ForwardedIncidentNotificationToED
  )

  val updateMessageTypesSentByArrivalTrader: Seq[MessageType] = Seq(
    UnloadingRemarks
  )

  val messageTypesSentByArrivalTrader: Seq[MessageType] = ArrivalNotification +: updateMessageTypesSentByArrivalTrader

  val messageTypesSentToArrivalTrader: Seq[MessageType] = Seq(
    GoodsReleaseNotification,
    UnloadingPermission,
    RejectionFromOfficeOfDestination,
    FunctionalNack,
    PositiveAcknowledge
  )

  val updateMessageTypeSentByTrader: Seq[MessageType] =
    updateMessageTypesSentByDepartureTrader ++ updateMessageTypesSentByArrivalTrader

  val values = (messageTypesSentByDepartureTrader ++
    messageTypesSentToDepartureTrader ++
    messageTypesSentByArrivalTrader ++
    messageTypesSentToArrivalTrader).distinct

  def findByCode(code: String): Option[MessageType] =
    values.find(_.code == code)

  def findByRootNode(root: String): Option[MessageType] =
    values.find(_.rootNode == root)

  implicit val messageTypeReads: Reads[MessageType] = Reads {
    case JsString(value) => findByCode(value).map(JsSuccess(_)).getOrElse(JsError())
    case _               => JsError()
  }

  implicit val messageTypeWrites: Writes[MessageType] = Writes {
    obj => JsString(obj.code)
  }

}
