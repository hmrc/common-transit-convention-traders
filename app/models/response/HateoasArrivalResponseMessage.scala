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

package models.response

import models.domain.ArrivalId
import models.domain.MessageId
import models.domain.MovementMessage
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.utils.CallOps._

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object HateoasArrivalResponseMessage {

  def apply(arrivalId: ArrivalId, messageId: MessageId, m: MovementMessage): JsObject = {
    val arrivalUrl: String = routing.routes.ArrivalsRouter.getArrivalMessage(arrivalId.toString, messageId.toString).urlWithContext
    val messageUrl: String = routing.routes.ArrivalsRouter.getArrival(arrivalId.toString).urlWithContext

    val received: LocalDateTime = m.received.getOrElse(m.dateTime).truncatedTo(ChronoUnit.SECONDS)

    Json.obj(
      "_links" -> Json.obj(
        "self"    -> Json.obj("href" -> arrivalUrl),
        "arrival" -> Json.obj("href" -> messageUrl)
      ),
      "arrivalId"   -> arrivalId.toString,
      "messageId"   -> messageId.toString,
      "received"    -> received,
      "messageType" -> m.messageType,
      "body"        -> m.message.toString
    )
  }
}
