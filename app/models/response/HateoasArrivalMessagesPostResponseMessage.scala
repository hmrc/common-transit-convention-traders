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
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.utils.CallOps._

import scala.xml.NodeSeq

object HateoasArrivalMessagesPostResponseMessage {

  def apply(arrivalId: ArrivalId, messageId: MessageId, messageType: String, message: NodeSeq): JsObject = {
    val messageUrl: String = routing.routes.ArrivalsRouter.getArrivalMessage(arrivalId.toString, messageId.toString).urlWithContext
    val arrivalUrl: String = routing.routes.ArrivalsRouter.getArrival(arrivalId.toString).urlWithContext

    Json.obj(
      "_links" -> Json.obj(
        "self"    -> Json.obj("href" -> messageUrl),
        "arrival" -> Json.obj("href" -> arrivalUrl)
      ),
      "arrivalId"   -> arrivalId.toString,
      "messageId"   -> messageId.toString,
      "messageType" -> messageType,
      "body"        -> message.toString,
      "_embedded" -> Json.obj(
        "notifications" -> Json.obj("requestId" -> arrivalUrl)
      )
    )
  }
}
