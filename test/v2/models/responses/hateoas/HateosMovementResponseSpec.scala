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

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import v2.models.EORINumber
import v2.models.MovementId
import v2.models.MovementReferenceNumber
import v2.models.responses.MovementResponse

import java.time.OffsetDateTime
import java.time.ZoneOffset

class HateosMovementResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  private val hexId    = Gen.listOfN(16, Gen.hexChar).map(_.mkString.toLowerCase)
  private val dateTime = OffsetDateTime.of(2022, 8, 15, 11, 45, 0, 0, ZoneOffset.UTC)

  "with a valid departure response, create a valid HateoasDepartureResponse" in {
    val departureId = MovementId(hexId.sample.get)

    val response = MovementResponse(
      _id = departureId,
      enrollmentEORINumber = EORINumber("GB123"),
      movementEORINumber = EORINumber("GB456"),
      movementReferenceNumber = Some(MovementReferenceNumber("MRN001")),
      created = dateTime,
      updated = dateTime
    )

    val actual      = HateoasDepartureResponse(departureId, response)
    val selfUri     = s"/customs/transits/movements/departures/${departureId.value}"
    val messagesUri = s"/customs/transits/movements/departures/${departureId.value}/messages"

    val expected = Json.obj(
      "_links" -> Json.obj(
        "self"     -> Json.obj("href" -> selfUri),
        "messages" -> Json.obj("href" -> messagesUri)
      ),
      "id"                      -> departureId,
      "movementReferenceNumber" -> "MRN001",
      "created"                 -> "2022-08-15T11:45:00Z",
      "updated"                 -> "2022-08-15T11:45:00Z",
      "enrollmentEORINumber"    -> "GB123",
      "movementEORINumber"      -> "GB456"
    )

    actual mustBe expected
  }
}