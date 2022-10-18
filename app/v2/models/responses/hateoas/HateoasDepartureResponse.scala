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
import v2.models.responses.DepartureResponse

object HateoasDepartureResponse extends HateoasResponse {

  def apply(departureId: MovementId, departureResponse: DepartureResponse): JsObject =
    Json
      .obj(
        "_links" -> Json.obj(
          "self"     -> Json.obj("href" -> departureUri(departureId)),
          "messages" -> Json.obj("href" -> messageIdsUri(departureId, None))
        ),
        "id"                      -> departureId,
        "movementReferenceNumber" -> departureResponse.movementReferenceNumber,
        "created"                 -> departureResponse.created,
        "updated"                 -> departureResponse.updated,
        "enrollmentEORINumber"    -> departureResponse.enrollmentEORINumber,
        "movementEORINumber"      -> departureResponse.movementEORINumber
      )

}
