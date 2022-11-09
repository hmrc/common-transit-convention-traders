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

  def messageUri(departureId: MovementId, messageId: MessageId) =
    routing.routes.DeparturesRouter.getMessage(departureId.value, messageId.value).urlWithContext

  def messageIdsUri(departureId: MovementId, receivedSince: Option[OffsetDateTime]) =
    routing.routes.DeparturesRouter
      .getMessageIds(
        departureId.value,
        receivedSince
      )
      .urlWithContext

  def departureUri(departureId: MovementId) =
    routing.routes.DeparturesRouter.getDeparture(departureId.value).urlWithContext

  def arrivalUri(arrivalId: MovementId) =
    routing.routes.ArrivalsRouter.getArrival(arrivalId.value).urlWithContext

  // TODO: When we do the arrival endpoint, this needs updating
  def arrivalMessageIdsUri(arrivalId: MovementId) =
    s"/customs/transits/movements/arrivals/${arrivalId.value}/messages"

}
