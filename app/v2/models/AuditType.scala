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

package v2.models

sealed abstract class AuditType(val name: String)

object AuditType {

  // IE015
  case object AmendmentAcceptance                             extends AuditType("AmendmentAcceptance")
  case object InvalidationDecision                            extends AuditType("InvalidationDecision")
  case object DeclarationAmendment                            extends AuditType("DeclarationAmendment")
  case object DeclarationInvalidationRequest                  extends AuditType("DeclarationInvalidationRequest")
  case object DeclarationData                                 extends AuditType("DeclarationData")
  case object Discrepancies                                   extends AuditType("Discrepancies")
  case object MRNAllocated                                    extends AuditType("MRNAllocated")
  case object ReleaseForTransit                               extends AuditType("ReleaseForTransit")
  case object RecoveryNotification                            extends AuditType("RecoveryNotification")
  case object WriteOffNotification                            extends AuditType("WriteOffNotification")
  case object NoReleaseForTransit                             extends AuditType("NoReleaseForTransit")
  case object RequestOfRelease                                extends AuditType("RequestOfRelease")
  case object GuaranteeNotValid                               extends AuditType("GuaranteeNotValid")
  case object RejectionFromOfficeOfDeparture                  extends AuditType("RejectionFromOfficeOfDeparture")
  case object ControlDecisionNotification                     extends AuditType("ControlDecisionNotification")
  case object PresentationNotificationForThePreLodgedDecision extends AuditType("PresentationNotificationForThePreLodgedDecision")
  case object FunctionalNack                                  extends AuditType("FunctionalNack")
  case object PositiveAcknowledge                             extends AuditType("PositiveAcknowledge")

  val values: Seq[AuditType] = Seq(
    DeclarationData,
    DeclarationAmendment,
    DeclarationInvalidationRequest,
    PresentationNotificationForThePreLodgedDecision,
    RequestOfRelease
  )

  def find(code: String): Option[AuditType] =
    values.find(_.name == code)
}
