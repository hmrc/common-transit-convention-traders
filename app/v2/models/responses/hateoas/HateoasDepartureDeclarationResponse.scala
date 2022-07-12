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
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import utils.CallOps.CallOps
import v2.models.MovementId

object HateoasDepartureDeclarationResponse {

  def messageUrl(movementId: MovementId) =
    // TODO: Fix when we do this route, as right now it only accepts an int.
    routes.DepartureMessagesController.sendMessageDownstream(DepartureId(123)).urlWithContext

  // TODO: Fix when we do this route, as right now it only accepts an int.
  def departureUrl(movementId: MovementId) = routes.DeparturesController.getDeparture(DepartureId(123)).urlWithContext

  def apply(departureId: MovementId): JsObject =
    Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj("href" -> departureUrl(departureId))
      ),
      "id" -> departureId.value,
      "_embedded" -> Json.obj(
        "messages" -> Json.obj(
          "_links" ->
            Json.obj("href" -> messageUrl(departureId))
        )
      )
    )
}
