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
import models.domain.ArrivalWithMessages
import models.domain.MessageId
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.utils.CallOps._
import utils.Utils

object HateoasResponseArrivalWithMessages {

  def apply(arrivalWithMessages: ArrivalWithMessages): JsObject = {
    val arrivalId: ArrivalId = arrivalWithMessages.arrivalId
    val messagesUrl: String  = routing.routes.ArrivalsRouter.getArrivalMessageIds(arrivalId.toString).urlWithContext

    Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj("href" -> messagesUrl)
      ),
      "_embedded" -> Json.obj(
        "messages" -> arrivalWithMessages.messages.map {
          x =>
            HateoasArrivalResponseMessage(arrivalId, MessageId(Utils.lastFragment(x.location).toInt), x)
        },
        "arrival" -> HateoasResponseArrival(
          arrivalId,
          arrivalWithMessages.created.toString,
          arrivalWithMessages.updated.toString,
          arrivalWithMessages.movementReferenceNumber
        )
      )
    )
  }
}
