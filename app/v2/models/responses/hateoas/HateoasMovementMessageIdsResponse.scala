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

package v2.models.responses.hateoas

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.models.MovementId
import v2.models.MovementType
import v2.models.responses.MessageSummary

import java.time.OffsetDateTime

object HateoasMovementMessageIdsResponse extends HateoasResponse {

  def apply(movementId: MovementId, messageIds: Seq[MessageSummary], receivedSince: Option[OffsetDateTime], movementType: MovementType): JsObject =
    Json.obj(
      "_links" -> Json.obj(
        "self"                    -> Json.obj("href" -> getMessagesUri(movementId, receivedSince, movementType)),
        movementType.movementType -> Json.obj("href" -> getMovementUri(movementId, movementType))
      ),
      "messages" -> messageIds.map(
        message =>
          Json.obj(
            "_links" -> Json.obj(
              "self"                    -> Json.obj("href" -> getMessageUri(movementId, message.id, movementType)),
              movementType.movementType -> Json.obj("href" -> getMovementUri(movementId, movementType))
            ),
            "id"                              -> message.id.value,
            s"${movementType.movementType}Id" -> movementId.value,
            "received"                        -> message.received,
            "type"                            -> message.messageType
          )
      )
    )

}