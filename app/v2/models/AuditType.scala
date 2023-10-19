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

  final case object AmendmentAcceptance                                extends AuditType("AmendmentAcceptance")
  final case object InvalidationDecision                               extends AuditType("InvalidationDecision")
  final case object DeclarationAmendment                               extends AuditType("DeclarationAmendment")
  final case object DeclarationInvalidationRequest                     extends AuditType("DeclarationInvalidationRequest")
  final case object DeclarationData                                    extends AuditType("DeclarationData")
  final case object Discrepancies                                      extends AuditType("Discrepancies")
  final case object GoodsReleaseNotification                           extends AuditType("GoodsReleaseNotification")
  final case object MRNAllocated                                       extends AuditType("MRNAllocated")
  final case object ReleaseForTransit                                  extends AuditType("ReleaseForTransit")
  final case object RecoveryNotification                               extends AuditType("RecoveryNotification")
  final case object UnloadingPermission                                extends AuditType("UnloadingPermission")
  final case object UnloadingRemarks                                   extends AuditType("UnloadingRemarks")
  final case object WriteOffNotification                               extends AuditType("WriteOffNotification")
  final case object NoReleaseForTransit                                extends AuditType("NoReleaseForTransit")
  final case object RequestOfRelease                                   extends AuditType("RequestOfRelease")
  final case object GuaranteeNotValid                                  extends AuditType("GuaranteeNotValid")
  final case object RejectionFromOfficeOfDeparture                     extends AuditType("RejectionFromOfficeOfDeparture")
  final case object RejectionFromOfficeOfDestination                   extends AuditType("RejectionFromOfficeOfDestination")
  final case object ControlDecisionNotification                        extends AuditType("ControlDecisionNotification")
  final case object RequestOnNonArrivedMovement                        extends AuditType("RequestOnNonArrivedMovement")
  final case object InformationAboutNonArrivedMovement                 extends AuditType("InformationAboutNonArrivedMovement")
  final case object PresentationNotificationForThePreLodgedDeclaration extends AuditType("PresentationNotificationForThePreLodgedDeclaration")
  final case object FunctionalNack                                     extends AuditType("FunctionalNack")
  final case object PositiveAcknowledge                                extends AuditType("PositiveAcknowledge")
  final case object ArrivalNotification                                extends AuditType("ArrivalNotification")
  final case object ForwardedIncidentNotificationToED                  extends AuditType("ForwardedIncidentNotificationToED")
  final case object LargeMessageSubmissionRequested                    extends AuditType("LargeMessageSubmissionRequested")
  final case object TraderFailedUploadEvent                            extends AuditType("TraderFailedUploadEvent")
  final case object ValidationFailed                                   extends AuditType("ValidationFailed")
  final case object SubmitArrivalNotificationFailed                    extends AuditType("SubmitArrivalNotificationFailed")
  final case object SubmitDeclarationFailed                            extends AuditType("SubmitDeclarationFailed")
  final case object CreateMovementDBFailed                             extends AuditType("CreateMovementDBFailed")
  final case object PushNotificationFailed                             extends AuditType("PushNotificationFailed")
  final case object PushNotificationUpdateFailed                       extends AuditType("PushNotificationUpdateFailed")
  final case object PushPullNotificationGetBoxFailed                   extends AuditType("PushPullNotificationGetBoxFailed")
  final case object AddMessageDBFailed                                 extends AuditType("AddMessageDBFailed")
  final case object SubmitAttachMessageFailed                          extends AuditType("SubmitAttachMessageFailed")
  final case object GetMovementsDBFailed                               extends AuditType("GetMovementsDBFailed")
  final case object GetMovementDBFailed                                extends AuditType("GetMovementDBFailed")
  final case object GetMovementMessagesDBFailed                        extends AuditType("GetMovementMessagesDBFailed")
  final case object GetMovementMessageDBFailed                         extends AuditType("GetMovementMessageDBFailed")

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
    RequestOfRelease,
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
    TraderFailedUploadEvent,
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
