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
import v2.models.DepartureId
import v2.models.responses.MessageResponseWithBody

import java.time.OffsetDateTime

object HateoasDepartureMessageIdsResponse extends HateoasResponse {

  def apply(departureId: DepartureId, messageIds: Seq[MessageResponseWithBody], receivedSince: Option[OffsetDateTime]): JsObject =
    Json.obj(
      "_links" -> Json.obj(
        "self"      -> Json.obj("href" -> messageIdsUri(departureId, receivedSince)),
        "departure" -> Json.obj("href" -> departureUri(departureId))
      ),
      "messages" -> messageIds.map(
        message =>
          Json.obj(
            "_links" -> Json.obj(
              "self"      -> Json.obj("href" -> messageUri(departureId, message.id)),
              "departure" -> Json.obj("href" -> departureUri(departureId))
            ),
            "id"          -> message.id.value,
            "departureId" -> departureId.value,
            "received"    -> message.received,
            "type"        -> message.messageType
          )
      )
    )

}
