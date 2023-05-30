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

import play.api.libs.json._
import v2.models.EORINumber
import v2.models.MovementReferenceNumber
import v2.models.MovementType
import v2.models.responses.MovementSummary

import java.time.OffsetDateTime

object HateoasMovementIdsResponse extends HateoasResponse {

  def apply(
    responses: Seq[MovementSummary],
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber]
  ): JsObject =
    Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj("href" -> getMovementsUri(movementType, updatedSince, movementEORI, movementReferenceNumber))
      ),
      movementType.urlFragment -> responses.map(
        response =>
          Json.obj(
            "_links" -> Json.obj(
              "self"     -> Json.obj("href" -> getMovementUri(response._id, movementType)),
              "messages" -> Json.obj("href" -> getMessagesUri(response._id, None, movementType))
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
