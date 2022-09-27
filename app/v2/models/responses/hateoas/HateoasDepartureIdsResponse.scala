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

import play.api.libs.json._
import v2.models.responses.DepartureResponse

object HateoasDepartureIdsResponse extends HateoasResponse {

  def apply(responses: Seq[DepartureResponse]): JsObject =
    Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj("href" -> "/customs/transits/movements/departures")
      ),
      "departures" -> responses.map(
        response =>
          Json.obj(
            "_links" -> Json.obj(
              "self"     -> Json.obj("href" -> departureUri(response._id)),
              "messages" -> Json.obj("href" -> s"${departureUri(response._id)}/messages")
            ),
            "id"                      -> response._id.value,
            "movementReferenceNumber" -> response.movementReferenceNumber,
            "created"                 -> response.created,
            "updated"                 -> response.updated,
            "enrollmentEORINumber"    -> response.enrollmentEORINumber,
            "movementEORINumber"      -> response.movementEORINumber
          )
      )
    )
}
