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

package models.responses.hateoas

import models.common.EORINumber
import models.common.ItemCount
import models.common.LocalReferenceNumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementReferenceNumber
import models.common.MovementType
import models.common.PageNumber
import utils.CallOps.*

import java.time.OffsetDateTime

trait HateoasResponse {

  def getMessageUri(movementId: MovementId, messageId: MessageId, movementType: MovementType): String =
    routing.routes.GenericRouting.getMessage(movementType, movementId.value, messageId.value).urlWithContext

  def getMessagesUri(
    movementId: MovementId,
    receivedSince: Option[OffsetDateTime],
    movementType: MovementType,
    page: Option[PageNumber] = None,
    count: Option[ItemCount] = None,
    receivedUntil: Option[OffsetDateTime] = None
  ): String =
    routing.routes.GenericRouting
      .getMessageIds(
        movementType,
        movementId.value,
        receivedSince,
        page,
        count,
        receivedUntil
      )
      .urlWithContext

  def getMovementUri(movementId: MovementId, movementType: MovementType): String =
    routing.routes.GenericRouting.getMovement(movementType = movementType, id = movementId.value).urlWithContext

  def getMovementsUri(
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber],
    page: Option[PageNumber],
    count: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime],
    localReferenceNumber: Option[LocalReferenceNumber]
  ): String =
    routing.routes.GenericRouting
      .getMovementForEori(updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil, localReferenceNumber, movementType)
      .urlWithContext

  def getMovementId(movementType: MovementType): String = if (movementType == MovementType.Departure) "departureId" else "arrivalId"
}
