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

import controllers.routes
import models.domain.DepartureId
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Writes
import play.api.libs.json.__
import utils.CallOps.CallOps
import v2.models.MessageId
import v2.models.MovementId
import v2.models.request.MessageType

object HateoasDepartureDeclarationResponse {

  def messageUrl(movementId: MovementId, messageId: MessageId) =
  // TODO: Fix when we do this route, as right now it only accepts an int.
    routes.DepartureMessagesController.getDepartureMessage(DepartureId(123), models.domain.MessageId(456)).urlWithContext

  // TODO: Fix when we do this route, as right now it only accepts an int.
  def departureUrl(movementId: MovementId) = routes.DeparturesController.getDeparture(DepartureId(123)).urlWithContext

  def apply(departureId: MovementId, messageId: MessageId, messageType: MessageType): JsObject =
    Json.obj(
    "_links" -> Json.obj(
      "self" -> Json.obj("href" -> messageUrl(departureId, messageId)),
        "departure" -> Json.obj("href" -> departureUrl(departureId))
      ),
      "departureId" -> departureId.value,
      "messageId" -> messageId.value,
      "messageType" -> messageType.code
    )
}
