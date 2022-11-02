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
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import routing.VersionedRouting
import v2.models.MessageId
import v2.models.MovementId
import v2.models.responses.MessageSummary

object HateoasDepartureMessageResponse extends HateoasResponse {

  def apply[A](departureId: MovementId, messageId: MessageId, messageSummary: MessageSummary, acceptHeader: String): JsObject =
    Json.obj(
      "_links" -> Json.obj(
        "self"      -> Json.obj("href" -> messageUri(departureId, messageId)),
        "departure" -> Json.obj("href" -> departureUri(departureId))
      ),
      "id"          -> messageId.value,
      "departureId" -> departureId.value,
      "received"    -> messageSummary.received,
      "type"        -> messageSummary.messageType
    ) ++ messageSummary.body
      .map(
        x => Json.obj("body" -> formatBody(acceptHeader, x))
      )
      .getOrElse(Json.obj())

  private def formatBody(acceptHeader: String, body: String): JsValue =
    acceptHeader match {
      case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON     => Json.parse(body)
      case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML => JsString(body)
    }
}
