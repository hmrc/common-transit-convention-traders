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

import models.common.EORINumber
import models.common.ItemCount
import models.common.LocalReferenceNumber
import models.common.MovementReferenceNumber
import models.common.MovementType
import models.common.PageNumber
import play.api.libs.json._
import v2_1.models.responses.PaginationMovementSummary

import java.time.OffsetDateTime

object HateoasMovementIdsResponse extends HateoasResponse {

  def apply(
    responses: PaginationMovementSummary,
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber],
    page: Option[PageNumber],
    count: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime],
    localReferenceNumber: Option[LocalReferenceNumber]
  ): JsObject =
    Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj(
          "href" -> getMovementsUri(movementType, updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil, localReferenceNumber)
        )
      ),
      "totalCount" -> responses.totalCount,
      movementType.urlFragment -> responses.movementSummary.map(
        response =>
          Json.obj(
            "_links" -> Json.obj(
              "self"     -> Json.obj("href" -> getMovementUri(response._id, movementType)),
              "messages" -> Json.obj("href" -> getMessagesUri(response._id, None, movementType))
            ),
            "id"                      -> response._id.value,
            "movementReferenceNumber" -> response.movementReferenceNumber,
            "localReferenceNumber"    -> response.localReferenceNumber,
            "created"                 -> response.created,
            "updated"                 -> response.updated,
            "enrollmentEORINumber"    -> response.enrollmentEORINumber,
            "movementEORINumber"      -> response.movementEORINumber
          )
      )
    )
}
