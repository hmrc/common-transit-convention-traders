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

import models.domain.Arrival
import models.domain.ArrivalId
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.utils.CallOps._

object HateoasResponseArrival {

  def apply(arrivalId: ArrivalId, created: String, updated: String, movementReferenceNumber: String): JsObject = {
    val arrivalUrl: String  = routing.routes.ArrivalsRouter.getArrival(arrivalId.toString).urlWithContext
    val messagesUrl: String = routing.routes.ArrivalsRouter.getArrivalMessageIds(arrivalId.toString).urlWithContext

    Json.obj(
      "id"                      -> arrivalId.toString,
      "created"                 -> created,
      "updated"                 -> updated,
      "movementReferenceNumber" -> movementReferenceNumber,
      "_links" -> Json.obj(
        "self"     -> Json.obj("href" -> arrivalUrl),
        "messages" -> Json.obj("href" -> messagesUrl)
      )
    )
  }

  def apply(arrival: Arrival): JsObject =
    apply(arrival.arrivalId, arrival.created.toString, arrival.updated.toString, arrival.movementReferenceNumber)
}
