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

sealed trait MessageType {
  def code: String
  def movementType: String
}

sealed abstract class DepartureMessageType(val code: String) extends MessageType {
  val movementType: String = "departures"
}

object MessageType {
  case object AmendmentAcceptance                             extends DepartureMessageType("IE004")
  case object InvalidationDecision                            extends DepartureMessageType("IE009")
  case object DeclarationAmendment                            extends DepartureMessageType("IE013")
  case object DeclarationInvalidationRequest                  extends DepartureMessageType("IE014")
  case object DeclarationData                                 extends DepartureMessageType("IE015")
  case object Discrepancies                                   extends DepartureMessageType("IE019")
  case object MRNAllocated                                    extends DepartureMessageType("IE028")
  case object ReleaseForTransit                               extends DepartureMessageType("IE029")
  case object RecoveryNotification                            extends DepartureMessageType("IE035")
  case object WriteOffNotification                            extends DepartureMessageType("IE045")
  case object NoReleaseForTransit                             extends DepartureMessageType("IE051")
  case object GuaranteeNotValid                               extends DepartureMessageType("IE055")
  case object RejectionFromOfficeOfDeparture                  extends DepartureMessageType("IE056")
  case object ControlDecisionNotification                     extends DepartureMessageType("IE060")
  case object PresentationNotificationForThePreLodgedDecision extends DepartureMessageType("IE170")
  case object FunctionalNack                                  extends DepartureMessageType("IE906") // TODO: This is also an arrival message
  case object PositiveAcknowledge                             extends DepartureMessageType("IE928") // TODO: This is also an arrival message

  val values: Seq[MessageType] = Seq(
    AmendmentAcceptance,
    InvalidationDecision,
    DeclarationAmendment,
    DeclarationInvalidationRequest,
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
    PresentationNotificationForThePreLodgedDecision,
    FunctionalNack,
    PositiveAcknowledge
  )

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
