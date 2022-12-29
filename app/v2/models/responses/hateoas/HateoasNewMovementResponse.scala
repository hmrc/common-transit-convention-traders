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
import v2.models.responses.BoxResponse

object HateoasNewMovementResponse extends HateoasResponse {

  def apply(movementId: MovementId, boxResponse: Option[BoxResponse], movementType: MovementType): JsObject = {
    val jsObject = Json.obj(
      "self"     -> Json.obj("href" -> getMovementUri(movementId, movementType)),
      "messages" -> Json.obj("href" -> getMessagesUri(movementId, None, movementType))
    )

    Json.obj("_links" -> jsObject) ++
      boxResponse
        .map(
          r => Json.obj("boxId" -> r.boxId.value)
        )
        .getOrElse(Json.obj())

  }

}
