/*
 * Copyright 2021 HM Revenue & Customs
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

package models

package response

import controllers.routes
import models.domain.DepartureId
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import utils.CallOps._

import scala.xml.NodeSeq

object HateoasDeparturePostResponseMessage {

  def apply(departureId: DepartureId, messageType: String, messageBody: NodeSeq, notificationsBox: Option[Box]): JsObject = {
    val departureUrl = routes.DeparturesController.getDeparture(departureId).urlWithContext
    val embedded = notificationsBox
      .map {
        box =>
          Json.obj(
            "_embedded" -> Json.obj(
              "notifications" -> Json.obj(
                "requestId" -> departureUrl,
                "boxId"     -> box.boxId.value,
                "boxName"   -> box.boxName
              )
            )
          )
      }
      .getOrElse(Json.obj())

    Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj("href" -> departureUrl)
      ),
      "departureId" -> departureId.toString,
      "messageType" -> messageType,
      "body"        -> messageBody.toString
    ) ++ embedded
  }
}
