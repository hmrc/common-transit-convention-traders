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
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Writes
import play.api.libs.json.__
import v2.models.MessageId
import v2.models.MovementId
import utils.CallOps.CallOps
import v2.models.request.MessageType

object HateoasDepartureLinks {
  implicit val hateoasDepartureLinksWrites: OWrites[HateoasDepartureLinks] = Json.writes[HateoasDepartureLinks]

  def messageUrl(movementId: MovementId, messageId: MessageId) =
  // TODO: Fix when we do this route, as right now it only accepts an int.
    routes.DepartureMessagesController.getDepartureMessage(DepartureId(123), models.domain.MessageId(456)).urlWithContext

  // TODO: Fix when we do this route, as right now it only accepts an int.
  def departureUrl(movementId: MovementId) = routes.DeparturesController.getDeparture(DepartureId(123)).urlWithContext

  def apply(movementId: MovementId, messageId: MessageId): HateoasDepartureLinks =
    HateoasDepartureLinks(HateoasHref(messageUrl(movementId, messageId)), HateoasHref(departureUrl(movementId)))
}
case class HateoasDepartureLinks(
  self: HateoasHref,
  departure: HateoasHref
)


object HateoasDepartureMessagePostResponse {

  // in this case, we want to force a different conversion then message type would give.
  implicit private val messageTypeWrites: Writes[MessageType] = Writes(t => JsString(t.code))

  implicit val hateoasMessagePostResponseWrites = (
    (__ \ "_links").write[HateoasDepartureLinks] and
      (__ \ "movementId").write[MovementId] and
      (__ \ "messageId").write[MessageId] and
      (__ \ "messageType").write[MessageType] and
      (__ \ "_embedded").write[HateoasEmbedded]
  )(unlift(HateoasDepartureMessagePostResponse.unapply))

  def apply(movementId: MovementId, messageId: MessageId, messageType: MessageType): HateoasDepartureMessagePostResponse =
    HateoasDepartureMessagePostResponse(
      HateoasDepartureLinks(movementId, messageId),
      movementId,
      messageId,
      messageType,
      HateoasEmbedded(None)
    )
}
case class HateoasDepartureMessagePostResponse(
  _links: HateoasDepartureLinks,
  movementId: MovementId,
  messageId: MessageId,
  messageType: MessageType,
  _embedded: HateoasEmbedded
)

