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

package v2.models.responses.hateoas

import v2.models._
import v2.utils.CallOps._

import java.time.OffsetDateTime

trait HateoasResponse {

  def getMessageUri(movementId: MovementId, messageId: MessageId, movementType: MovementType): String =
    movementType match {
      case MovementType.Departure => routing.routes.DeparturesRouter.getMessage(movementId.value, messageId.value).urlWithContext
      case MovementType.Arrival   => routing.routes.ArrivalsRouter.getArrivalMessage(movementId.value, messageId.value).urlWithContext
    }

  def getMessagesUri(movementId: MovementId, receivedSince: Option[OffsetDateTime], movementType: MovementType): String =
    movementType match {
      case MovementType.Arrival =>
        routing.routes.ArrivalsRouter
          .getArrivalMessageIds(
            movementId.value,
            receivedSince
          )
          .urlWithContext
      case MovementType.Departure =>
        routing.routes.DeparturesRouter
          .getMessageIds(
            movementId.value,
            receivedSince
          )
          .urlWithContext
    }

  def getMovementUri(movementId: MovementId, movementType: MovementType): String =
    movementType match {
      case MovementType.Arrival   => routing.routes.ArrivalsRouter.getArrival(movementId.value).urlWithContext
      case MovementType.Departure => routing.routes.DeparturesRouter.getDeparture(movementId.value).urlWithContext
    }

  def getMovementsUri(movementType: MovementType, updatedSince: Option[OffsetDateTime], movementEORI: Option[EORINumber]) =
    movementType match {
      case MovementType.Arrival   => routing.routes.ArrivalsRouter.getArrivalsForEori(updatedSince, movementEORI).urlWithContext
      case MovementType.Departure => routing.routes.DeparturesRouter.getDeparturesForEori(updatedSince, movementEORI).urlWithContext
    }
}
