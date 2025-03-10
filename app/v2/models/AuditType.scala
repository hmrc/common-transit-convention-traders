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

package v2.models

sealed abstract class AuditType(val name: String)

object AuditType {

  case object AmendmentAcceptance                                extends AuditType("AmendmentAcceptance")
  case object InvalidationDecision                               extends AuditType("InvalidationDecision")
  case object DeclarationAmendment                               extends AuditType("DeclarationAmendment")
  case object DeclarationInvalidationRequest                     extends AuditType("DeclarationInvalidationRequest")
  case object DeclarationData                                    extends AuditType("DeclarationData")
  case object Discrepancies                                      extends AuditType("Discrepancies")
  case object GoodsReleaseNotification                           extends AuditType("GoodsReleaseNotification")
  case object MRNAllocated                                       extends AuditType("MRNAllocated")
  case object ReleaseForTransit                                  extends AuditType("ReleaseForTransit")
  case object RecoveryNotification                               extends AuditType("RecoveryNotification")
  case object UnloadingPermission                                extends AuditType("UnloadingPermission")
  case object UnloadingRemarks                                   extends AuditType("UnloadingRemarks")
  case object WriteOffNotification                               extends AuditType("WriteOffNotification")
  case object NoReleaseForTransit                                extends AuditType("NoReleaseForTransit")
  case object GuaranteeNotValid                                  extends AuditType("GuaranteeNotValid")
  case object RejectionFromOfficeOfDeparture                     extends AuditType("RejectionFromOfficeOfDeparture")
  case object RejectionFromOfficeOfDestination                   extends AuditType("RejectionFromOfficeOfDestination")
  case object ControlDecisionNotification                        extends AuditType("ControlDecisionNotification")
  case object RequestOnNonArrivedMovement                        extends AuditType("RequestOnNonArrivedMovement")
  case object InformationAboutNonArrivedMovement                 extends AuditType("InformationAboutNonArrivedMovement")
  case object PresentationNotificationForThePreLodgedDeclaration extends AuditType("PresentationNotificationForThePreLodgedDeclaration")
  case object FunctionalNack                                     extends AuditType("FunctionalNack")
  case object PositiveAcknowledge                                extends AuditType("PositiveAcknowledge")
  case object ArrivalNotification                                extends AuditType("ArrivalNotification")
  case object ForwardedIncidentNotificationToED                  extends AuditType("ForwardedIncidentNotificationToED")
  case object LargeMessageSubmissionRequested                    extends AuditType("LargeMessageSubmissionRequested")
  case object TraderFailedUpload                                 extends AuditType("TraderFailedUpload")
  case object TraderToNCTSSubmissionSuccessful                   extends AuditType("TraderToNCTSSubmissionSuccessful")
  case object CustomerRequestedMissingMovement                   extends AuditType("CustomerRequestedMissingMovement")
  case object ValidationFailed                                   extends AuditType("ValidationFailed")
  case object SubmitArrivalNotificationFailed                    extends AuditType("SubmitArrivalNotificationFailed")
  case object SubmitDeclarationFailed                            extends AuditType("SubmitDeclarationFailed")
  case object CreateMovementDBFailed                             extends AuditType("CreateMovementDBFailed")
  case object PushNotificationFailed                             extends AuditType("PushNotificationFailed")
  case object PushNotificationUpdateFailed                       extends AuditType("PushNotificationUpdateFailed")
  case object PushPullNotificationGetBoxFailed                   extends AuditType("PushPullNotificationGetBoxFailed")
  case object AddMessageDBFailed                                 extends AuditType("AddMessageDBFailed")
  case object SubmitAttachMessageFailed                          extends AuditType("SubmitAttachMessageFailed")
  case object GetMovementsDBFailed                               extends AuditType("GetMovementsDBFailed")
  case object GetMovementDBFailed                                extends AuditType("GetMovementDBFailed")
  case object GetMovementMessagesDBFailed                        extends AuditType("GetMovementMessagesDBFailed")
  case object GetMovementMessageDBFailed                         extends AuditType("GetMovementMessageDBFailed")

  val values: Seq[AuditType] = Seq(
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
    UnloadingPermission,
    GuaranteeNotValid,
    RejectionFromOfficeOfDeparture,
    RejectionFromOfficeOfDestination,
    ControlDecisionNotification,
    GoodsReleaseNotification,
    PresentationNotificationForThePreLodgedDeclaration,
    FunctionalNack,
    RequestOnNonArrivedMovement,
    PositiveAcknowledge,
    ArrivalNotification,
    UnloadingRemarks,
    InformationAboutNonArrivedMovement,
    ForwardedIncidentNotificationToED,
    LargeMessageSubmissionRequested,
    TraderFailedUpload,
    TraderToNCTSSubmissionSuccessful,
    CustomerRequestedMissingMovement,
    ValidationFailed,
    SubmitArrivalNotificationFailed,
    SubmitDeclarationFailed,
    CreateMovementDBFailed,
    PushNotificationFailed,
    PushNotificationUpdateFailed,
    PushPullNotificationGetBoxFailed,
    AddMessageDBFailed,
    SubmitAttachMessageFailed,
    GetMovementsDBFailed,
    GetMovementMessagesDBFailed,
    GetMovementMessageDBFailed
  )

  def find(code: String): Option[AuditType] =
    values.find(_.name == code)

}
