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
import models.domain.Departure
import play.api.libs.json._
import utils.CallOps._

object HateaosResponseDeparture {

  def apply(departureId: String, created: String, updated: String, movementReferenceNumber: Option[String], status: String): JsObject = {
    val departureUrl = routes.DeparturesController.getDeparture(departureId).urlWithContext
    val messagesUrl = routes.DepartureMessagesController.getDepartureMessages(departureId).urlWithContext

    JsObject(
      Json.obj(
      "id" -> departureId,
      "created" -> created,
      "updated" -> updated,
      "movementReferenceNumber" -> movementReferenceNumber,
      "status" -> status,
      "_links" -> Json.obj(
        "self"    -> Json.obj("href" -> departureUrl),
        "messages"    -> Json.obj("href" -> messagesUrl)
      )
    ).fields.filter(t => t._2 != JsNull))
  }

  def apply(departure: Departure): JsObject = {
    apply(departure.departureId.toString, departure.created.toString, departure.updated.toString, departure.movementReferenceNumber, departure.status)
  }
}
