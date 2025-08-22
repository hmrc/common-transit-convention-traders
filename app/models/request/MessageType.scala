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

package models.request

import models.AuditType
import models.common.MovementType
import play.api.libs.json.JsError
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.Reads
import play.api.libs.json.Writes

enum MessageType(val code: String, val movementType: MovementType, val rootNode: String, val auditType: AuditType) {
  case ArrivalNotification extends MessageType("IE007", MovementType.Arrival, "CC007C", AuditType.ArrivalNotification)
  case GoodsReleaseNotification extends MessageType("IE025", MovementType.Arrival, "CC025C", AuditType.GoodsReleaseNotification)
  case UnloadingPermission extends MessageType("IE043", MovementType.Arrival, "CC043C", AuditType.UnloadingPermission)
  case UnloadingRemarks extends MessageType("IE044", MovementType.Arrival, "CC044C", AuditType.UnloadingRemarks)
  case RejectionFromOfficeOfDestination extends MessageType("IE057", MovementType.Arrival, "CC057C", AuditType.RejectionFromOfficeOfDestination)
  case AmendmentAcceptance extends MessageType("IE004", MovementType.Departure, "CC004C", AuditType.AmendmentAcceptance)
  case InvalidationDecision extends MessageType("IE009", MovementType.Departure, "CC009C", AuditType.InvalidationDecision)
  case DeclarationAmendment extends MessageType("IE013", MovementType.Departure, "CC013C", AuditType.DeclarationAmendment)
  case DeclarationInvalidationRequest extends MessageType("IE014", MovementType.Departure, "CC014C", AuditType.DeclarationInvalidationRequest)
  case DeclarationData extends MessageType("IE015", MovementType.Departure, "CC015C", AuditType.DeclarationData)
  case Discrepancies extends MessageType("IE019", MovementType.Departure, "CC019C", AuditType.Discrepancies)
  case MRNAllocated extends MessageType("IE028", MovementType.Departure, "CC028C", AuditType.MRNAllocated)
  case ReleaseForTransit extends MessageType("IE029", MovementType.Departure, "CC029C", AuditType.ReleaseForTransit)
  case RecoveryNotification extends MessageType("IE035", MovementType.Departure, "CC035C", AuditType.RecoveryNotification)
  case WriteOffNotification extends MessageType("IE045", MovementType.Departure, "CC045C", AuditType.WriteOffNotification)
  case NoReleaseForTransit extends MessageType("IE051", MovementType.Departure, "CC051C", AuditType.NoReleaseForTransit)
  case GuaranteeNotValid extends MessageType("IE055", MovementType.Departure, "CC055C", AuditType.GuaranteeNotValid)
  case RejectionFromOfficeOfDeparture extends MessageType("IE056", MovementType.Departure, "CC056C", AuditType.RejectionFromOfficeOfDeparture)
  case ControlDecisionNotification extends MessageType("IE060", MovementType.Departure, "CC060C", AuditType.ControlDecisionNotification)
  case ForwardedIncidentNotificationToED extends MessageType("IE182", MovementType.Departure, "CC182C", AuditType.ForwardedIncidentNotificationToED)
  case PresentationNotificationForThePreLodgedDeclaration extends MessageType("IE170", MovementType.Departure, "CC170C", AuditType.PresentationNotificationForThePreLodgedDeclaration)
  case FunctionalNack extends MessageType("IE906", MovementType.Departure, "CC906C", AuditType.FunctionalNack)
  case PositiveAcknowledge extends MessageType("IE928", MovementType.Departure, "CC928C", AuditType.PositiveAcknowledge)
}

object MessageType {

  val updateMessageTypesSentByDepartureTrader: Seq[MessageType] = Seq(
    DeclarationAmendment,
    DeclarationInvalidationRequest,
    PresentationNotificationForThePreLodgedDeclaration
  )

  val messageTypesSentByDepartureTrader: Seq[MessageType] = DeclarationData +: updateMessageTypesSentByDepartureTrader

  val updateMessageTypesSentByArrivalTrader: Seq[MessageType] = Seq(
    UnloadingRemarks
  )

  val messageTypesSentByArrivalTrader: Seq[MessageType] = ArrivalNotification +: updateMessageTypesSentByArrivalTrader
  

  def findByCode(code: String): Option[MessageType] =
    values.find(_.code == code)

  implicit val messageTypeReads: Reads[MessageType] = Reads {
    case JsString(value) => findByCode(value).map(JsSuccess(_)).getOrElse(JsError())
    case _               => JsError()
  }

  implicit val messageTypeWrites: Writes[MessageType] = Writes {
    obj => JsString(obj.code)
  }
}
