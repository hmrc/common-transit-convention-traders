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

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.models.MessageId
import v2.models.MovementId
import v2.models.responses.MessageResponse

object HateoasDepartureMessageResponse {

  lazy val prefix =
    if (routing.routes.DeparturesRouter.submitDeclaration().url.startsWith("/customs/transits")) ""
    else "/customs/transits"

  def selfUrl(departureId: MovementId, messageId: MessageId) =
    prefix + routing.routes.DeparturesRouter.getMessage(departureId.value, messageId.value).url

  // TODO: When we do the departure endpoint, this needs updating
  def departureUrl(departureId: MovementId) =
    s"/customs/transits/movements/departures/${departureId.value}"
  // prefix + routing.routes.DeparturesRouter.getMessage(departureId.value, "1").url

  def apply(departureId: MovementId, messageId: MessageId, messageResponse: MessageResponse): JsObject =
    Json.obj(
      "_links" -> Json.obj(
        "self"      -> Json.obj("href" -> selfUrl(departureId, messageId)),
        "departure" -> Json.obj("href" -> departureUrl(departureId))
      ),
      "departureId" -> departureId.value,
      "messageId"   -> messageResponse.id,
      "received"    -> messageResponse.received.toLocalDateTime,
      "messageType" -> messageResponse.messageType
    ) ++ messageResponse.body
      .map(
        x => Json.obj("body" -> x)
      )
      .getOrElse(Json.obj())

}
