/*
 * Copyright 2022 HM Revenue & Customs
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

sealed trait MessageType {
  def code: String
  def movementType: String
  def rootNode: String
  def auditType: AuditType
}

sealed abstract class DepartureMessageType(val code: String, val rootNode: String, val auditType: AuditType) extends MessageType {
  val movementType: String = "departures"
}

object MessageType {
  case object AmendmentAcceptance            extends DepartureMessageType("IE004", "CC004C", AuditType.AmendmentAcceptance)
  case object InvalidationDecision           extends DepartureMessageType("IE009", "CC009C", AuditType.InvalidationDecision)
  case object DeclarationAmendment           extends DepartureMessageType("IE013", "CC013C", AuditType.DeclarationAmendment)
  case object DeclarationInvalidationRequest extends DepartureMessageType("IE014", "CC014C", AuditType.DeclarationInvalidationRequest)
  case object DeclarationData                extends DepartureMessageType("IE015", "CC015C", AuditType.DeclarationData)
  case object Discrepancies                  extends DepartureMessageType("IE019", "CC019C", AuditType.Discrepancies)
  case object MRNAllocated                   extends DepartureMessageType("IE028", "CC028C", AuditType.MRNAllocated)
  case object ReleaseForTransit              extends DepartureMessageType("IE029", "CC029C", AuditType.ReleaseForTransit)
  case object RecoveryNotification           extends DepartureMessageType("IE035", "CC035C", AuditType.RecoveryNotification)
  case object WriteOffNotification           extends DepartureMessageType("IE045", "CC045C", AuditType.WriteOffNotification)
  case object NoReleaseForTransit            extends DepartureMessageType("IE051", "CC051C", AuditType.NoReleaseForTransit)
  case object RequestOfRelease               extends DepartureMessageType("IE054", "CC054C", AuditType.RequestOfRelease)
  case object GuaranteeNotValid              extends DepartureMessageType("IE055", "CC055C", AuditType.GuaranteeNotValid)
  case object RejectionFromOfficeOfDeparture extends DepartureMessageType("IE056", "CC056C", AuditType.RejectionFromOfficeOfDeparture)
  case object ControlDecisionNotification    extends DepartureMessageType("IE060", "CC060C", AuditType.ControlDecisionNotification)

  case object PresentationNotificationForThePreLodgedDecision
      extends DepartureMessageType("IE170", "CC170C", AuditType.PresentationNotificationForThePreLodgedDecision)
  case object FunctionalNack      extends DepartureMessageType("IE906", "CC906C", AuditType.FunctionalNack)      // TODO: This is also an arrival message
  case object PositiveAcknowledge extends DepartureMessageType("IE928", "CC928C", AuditType.PositiveAcknowledge) // TODO: This is also an arrival message

  val updateDepartureValues = Seq(
    DeclarationAmendment,
    DeclarationInvalidationRequest,
    RequestOfRelease,
    PresentationNotificationForThePreLodgedDecision
  )

  val departureValues: Seq[MessageType] = Seq(
    AmendmentAcceptance,
    InvalidationDecision,
    DeclarationData,
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
    PositiveAcknowledge
  )
  val values = updateDepartureValues ++ departureValues

  def find(code: String): Option[MessageType] =
    values.find(_.code == code)

  implicit val messageTypeReads: Reads[MessageType] = Reads {
    case JsString(value) => find(value).map(JsSuccess(_)).getOrElse(JsError())
    case _               => JsError()
  }

  implicit val messageTypeWrites: Writes[MessageType] = Writes {
    obj => JsString(obj.code)
  }

}
