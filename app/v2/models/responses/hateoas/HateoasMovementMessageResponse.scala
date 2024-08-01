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

package v2.models.responses.hateoas

import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.models.MessageStatus
import v2.models.responses.MessageSummary

object HateoasMovementMessageResponse extends HateoasResponse {

  def apply(movementId: MovementId, messageId: MessageId, messageSummary: MessageSummary, movementType: MovementType): JsObject = {
    val jsLinksObject = Json.obj(
      "self"                    -> Json.obj("href" -> getMessageUri(movementId, messageId, movementType)),
      movementType.movementType -> Json.obj("href" -> getMovementUri(movementId, movementType))
    )

    val jsObject1 = if (messageSummary.status.contains(MessageStatus.Pending)) {
      Json.obj(
        "_links"                          -> jsLinksObject,
        "id"                              -> messageId.value,
        s"${movementType.movementType}Id" -> movementId.value,
        "received"                        -> messageSummary.received
      )
    } else {
      Json.obj(
        "_links"                          -> jsLinksObject,
        "id"                              -> messageId.value,
        s"${movementType.movementType}Id" -> movementId.value,
        "received"                        -> messageSummary.received,
        "type"                            -> messageSummary.messageType
      )
    }

    jsObject1 ++ messageSummary.status
      .map(
        status => Json.obj("status" -> Json.toJson(status))
      )
      .getOrElse(Json.obj()) ++ messageSummary.body
      .map(
        payload => Json.obj("body" -> payload.asJson)
      )
      .getOrElse(Json.obj())
  }
}
