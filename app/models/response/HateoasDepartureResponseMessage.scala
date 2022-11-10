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

package models.response

import models.domain.DepartureId
import models.domain.MessageId
import models.domain.MovementMessage
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.utils.CallOps._

object HateoasDepartureResponseMessage {

  def apply(departureId: DepartureId, messageId: MessageId, m: MovementMessage): JsObject = {
    val departureUrl = routing.routes.DeparturesRouter.getMessage(departureId.toString, messageId.toString).urlWithContext
    val messageUrl   = routing.routes.DeparturesRouter.getDeparture(departureId.toString).urlWithContext

    Json.obj(
      "_links" -> Json.obj(
        "self"      -> Json.obj("href" -> departureUrl),
        "departure" -> Json.obj("href" -> messageUrl)
      ),
      "departureId" -> departureId.toString,
      "messageId"   -> messageId.toString,
      "received"    -> m.dateTime,
      "messageType" -> m.messageType,
      "body"        -> m.message.toString
    )
  }
}
