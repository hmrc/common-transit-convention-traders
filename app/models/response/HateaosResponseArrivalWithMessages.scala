/*
 * Copyright 2020 HM Revenue & Customs
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
import models.domain.ArrivalWithMessages
import play.api.libs.json.{JsObject, Json}
import utils.CallOps._
import utils.Utils

object HateaosResponseArrivalWithMessages {

  def apply(arrivalWithMessages: ArrivalWithMessages): JsObject = {
    val arrivalId = arrivalWithMessages.arrivalId.toString
    val messagesUrl = routes.ArrivalMessagesController.getArrivalMessages(arrivalId).urlWithContext

    Json.obj(
      "_links" -> Json.arr(
        Json.obj("self" -> Json.obj("href" -> messagesUrl))
      ),
      "_embedded" -> Json.arr(
        Json.obj("messages" -> arrivalWithMessages.messages.map {
          x =>
            HateaosArrivalResponseMessage(arrivalId, Utils.lastFragment(x.location), x)
        }),
        Json.obj("arrival" -> HateaosResponseArrival(
          arrivalId,
          arrivalWithMessages.created.toString,
          arrivalWithMessages.updated.toString,
          arrivalWithMessages.movementReferenceNumber,
          arrivalWithMessages.status
        ))
      )
    )
  }
}
