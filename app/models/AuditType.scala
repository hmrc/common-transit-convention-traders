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

package models

enum AuditType(val name: String) {
  case AmendmentAcceptance                                extends AuditType("AmendmentAcceptance")
  case InvalidationDecision                               extends AuditType("InvalidationDecision")
  case DeclarationAmendment                               extends AuditType("DeclarationAmendment")
  case DeclarationInvalidationRequest                     extends AuditType("DeclarationInvalidationRequest")
  case DeclarationData                                    extends AuditType("DeclarationData")
  case Discrepancies                                      extends AuditType("Discrepancies")
  case GoodsReleaseNotification                           extends AuditType("GoodsReleaseNotification")
  case MRNAllocated                                       extends AuditType("MRNAllocated")
  case ReleaseForTransit                                  extends AuditType("ReleaseForTransit")
  case RecoveryNotification                               extends AuditType("RecoveryNotification")
  case UnloadingPermission                                extends AuditType("UnloadingPermission")
  case UnloadingRemarks                                   extends AuditType("UnloadingRemarks")
  case WriteOffNotification                               extends AuditType("WriteOffNotification")
  case NoReleaseForTransit                                extends AuditType("NoReleaseForTransit")
  case GuaranteeNotValid                                  extends AuditType("GuaranteeNotValid")
  case RejectionFromOfficeOfDeparture                     extends AuditType("RejectionFromOfficeOfDeparture")
  case RejectionFromOfficeOfDestination                   extends AuditType("RejectionFromOfficeOfDestination")
  case ControlDecisionNotification                        extends AuditType("ControlDecisionNotification")
  case RequestOnNonArrivedMovement                        extends AuditType("RequestOnNonArrivedMovement")
  case InformationAboutNonArrivedMovement                 extends AuditType("InformationAboutNonArrivedMovement")
  case PresentationNotificationForThePreLodgedDeclaration extends AuditType("PresentationNotificationForThePreLodgedDeclaration")
  case FunctionalNack                                     extends AuditType("FunctionalNack")
  case PositiveAcknowledge                                extends AuditType("PositiveAcknowledge")
  case ArrivalNotification                                extends AuditType("ArrivalNotification")
  case ForwardedIncidentNotificationToED                  extends AuditType("ForwardedIncidentNotificationToED")
  case LargeMessageSubmissionRequested                    extends AuditType("LargeMessageSubmissionRequested")
  case TraderFailedUpload                                 extends AuditType("TraderFailedUpload")
  case TraderToNCTSSubmissionSuccessful                   extends AuditType("TraderToNCTSSubmissionSuccessful")
  case CustomerRequestedMissingMovement                   extends AuditType("CustomerRequestedMissingMovement")
  case ValidationFailed                                   extends AuditType("ValidationFailed")
  case SubmitArrivalNotificationFailed                    extends AuditType("SubmitArrivalNotificationFailed")
  case SubmitDeclarationFailed                            extends AuditType("SubmitDeclarationFailed")
  case CreateMovementDBFailed                             extends AuditType("CreateMovementDBFailed")
  case PushNotificationFailed                             extends AuditType("PushNotificationFailed")
  case PushNotificationUpdateFailed                       extends AuditType("PushNotificationUpdateFailed")
  case PushPullNotificationGetBoxFailed                   extends AuditType("PushPullNotificationGetBoxFailed")
  case AddMessageDBFailed                                 extends AuditType("AddMessageDBFailed")
  case SubmitAttachMessageFailed                          extends AuditType("SubmitAttachMessageFailed")
  case GetMovementsDBFailed                               extends AuditType("GetMovementsDBFailed")
  case GetMovementDBFailed                                extends AuditType("GetMovementDBFailed")
  case GetMovementMessagesDBFailed                        extends AuditType("GetMovementMessagesDBFailed")
  case GetMovementMessageDBFailed                         extends AuditType("GetMovementMessageDBFailed")
}

object AuditType {
  def find(code: String): Option[AuditType] =
    AuditType.values.find(_.name == code)
}
