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

package v2_1.models.responses.hateoas

import models.common.ItemCount
import models.common.MovementId
import models.common.MovementType
import models.common.PageNumber
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2_1.models.MessageStatus
import v2_1.models.responses.PaginationMessageSummary

import java.time.OffsetDateTime

object HateoasMovementMessageIdsResponse extends HateoasResponse {

  def apply(
    movementId: MovementId,
    messageIds: PaginationMessageSummary,
    receivedSince: Option[OffsetDateTime],
    movementType: MovementType,
    page: Option[PageNumber],
    count: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime]
  ): JsObject =
    Json.obj(
      "_links" -> Json.obj(
        "self"                    -> Json.obj("href" -> getMessagesUri(movementId, receivedSince, movementType, page, count, receivedUntil)),
        movementType.movementType -> Json.obj("href" -> getMovementUri(movementId, movementType))
      ),
      "totalCount" -> messageIds.totalCount.value,
      "messages" -> messageIds.messageSummary.map {
        message =>
          val jsMessageLinksObject = Json.obj(
            "self"                    -> Json.obj("href" -> getMessageUri(movementId, message.id, movementType)),
            movementType.movementType -> Json.obj("href" -> getMovementUri(movementId, movementType))
          )
          (if (message.status.contains(MessageStatus.Pending)) {
             Json.obj(
               "_links"                          -> jsMessageLinksObject,
               "id"                              -> message.id.value,
               s"${movementType.movementType}Id" -> movementId.value,
               "received"                        -> message.received
             )
           } else {
             Json.obj(
               "_links"                          -> jsMessageLinksObject,
               "id"                              -> message.id.value,
               s"${movementType.movementType}Id" -> movementId.value,
               "received"                        -> message.received,
               "type"                            -> message.messageType
             )
           }) ++ message.status
            .map(
              status => Json.obj("status" -> Json.toJson(status))
            )
            .getOrElse(Json.obj())
      }
    )

}
