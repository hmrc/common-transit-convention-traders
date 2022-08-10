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
import models.domain.Departure
import models.domain.DepartureId
import play.api.libs.json._
import utils.CallOps._

object HateoasResponseDeparture {

  def apply(departureId: DepartureId, created: String, updated: String, movementReferenceNumber: Option[String]): JsObject = {
    val departureUrl = routes.DeparturesController.getDeparture(departureId).urlWithContext
    val messagesUrl  = routing.routes.DeparturesRouter.getMessageIds(departureId.toString).urlWithContext

    JsObject(
      Json
        .obj(
          "id"                      -> departureId.toString,
          "created"                 -> created,
          "updated"                 -> updated,
          "movementReferenceNumber" -> movementReferenceNumber,
          "_links" -> Json.obj(
            "self"     -> Json.obj("href" -> departureUrl),
            "messages" -> Json.obj("href" -> messagesUrl)
          )
        )
        .fields
        .filter(
          t => t._2 != JsNull
        )
    )
  }

  def apply(departure: Departure): JsObject =
    apply(departure.departureId, departure.created.toString, departure.updated.toString, departure.movementReferenceNumber)
}
