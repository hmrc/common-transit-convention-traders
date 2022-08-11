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

import controllers.routes
import models.domain.DepartureId
import models.domain.MessageId
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import utils.CallOps._

import scala.xml.NodeSeq

object HateoasDepartureMessagesPostResponseMessage {

  def apply(departureId: DepartureId, messageId: MessageId, messageType: String, message: NodeSeq): JsObject = {
    val messageUrl   = routing.routes.DeparturesRouter.getMessage(departureId.toString, messageId.toString).urlWithContext
    val departureUrl = routes.DeparturesController.getDeparture(departureId).urlWithContext

    Json.obj(
      "_links" -> Json.obj(
        "self"      -> Json.obj("href" -> messageUrl),
        "departure" -> Json.obj("href" -> departureUrl)
      ),
      "departureId" -> departureId.toString,
      "messageId"   -> messageId.toString,
      "messageType" -> messageType,
      "body"        -> message.toString,
      "_embedded" -> Json.obj(
        "notifications" -> Json.obj("requestId" -> departureUrl)
      )
    )
  }
}
