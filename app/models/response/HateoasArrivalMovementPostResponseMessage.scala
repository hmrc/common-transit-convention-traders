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

import models.Box
import models.domain.ArrivalId
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.utils.CallOps.CallOps

import scala.xml.NodeSeq

object HateoasArrivalMovementPostResponseMessage {

  def apply(arrivalId: ArrivalId, messageType: String, message: NodeSeq, notificationsBox: Option[Box]): JsObject = {
    val arrivalUrl: String = routing.routes.ArrivalsRouter.getArrival(arrivalId.toString).urlWithContext

    val embedded: JsObject = notificationsBox
      .map {
        box =>
          Json.obj(
            "_embedded" -> Json.obj(
              "notifications" -> Json.obj(
                "requestId" -> arrivalUrl,
                "boxId"     -> box.boxId.value,
                "boxName"   -> box.boxName
              )
            )
          )
      }
      .getOrElse(Json.obj())

    Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj("href" -> arrivalUrl)
      ),
      "arrivalId"   -> arrivalId.toString,
      "messageType" -> messageType,
      "body"        -> message.toString
    ) ++ embedded

  }
}
