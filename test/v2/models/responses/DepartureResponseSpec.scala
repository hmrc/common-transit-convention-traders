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

package v2.models.responses

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import v2.models.EORINumber
import v2.models.MovementId
import v2.models.MovementReferenceNumber

import java.time.OffsetDateTime
import java.time.ZoneOffset

class DepartureResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  private val hexId    = Gen.listOfN(16, Gen.hexChar).map(_.mkString.toLowerCase)
  private val dateTime = OffsetDateTime.of(2022, 8, 15, 11, 30, 0, 0, ZoneOffset.UTC)

  "when DepartureResponse is serialized, return an appropriate JsObject" in {
    val departureId = MovementId(hexId.sample.get)

    val actual = DepartureResponse.departureResponseFormat.writes(
      DepartureResponse(
        _id = departureId,
        enrollmentEORINumber = EORINumber("GB123"),
        movementEORINumber = EORINumber("GB456"),
        movementReferenceNumber = Some(MovementReferenceNumber("MRN001")),
        created = dateTime,
        updated = dateTime
      )
    )

    val expected = Json.obj(
      "_id"                     -> departureId,
      "enrollmentEORINumber"    -> "GB123",
      "movementEORINumber"      -> "GB456",
      "movementReferenceNumber" -> "MRN001",
      "created"                 -> "2022-08-15T11:30:00Z", // due to OffsetDateTime in UTC
      "updated"                 -> "2022-08-15T11:30:00Z"
    )
    actual mustBe expected
  }

  "when an appropriate JsObject is deserialized, return a DepartureResponse" in {
    val departureId = MovementId(hexId.sample.get)

    val expected =
      DepartureResponse(
        _id = departureId,
        enrollmentEORINumber = EORINumber("GB123"),
        movementEORINumber = EORINumber("GB456"),
        movementReferenceNumber = Some(MovementReferenceNumber("MRN001")),
        created = dateTime,
        updated = dateTime
      )

    val actual = DepartureResponse.departureResponseFormat.reads(
      Json.obj(
        "_id"                     -> departureId,
        "enrollmentEORINumber"    -> "GB123",
        "movementEORINumber"      -> "GB456",
        "movementReferenceNumber" -> "MRN001",
        "created"                 -> "2022-08-15T11:30:00Z", // due to OffsetDateTime in UTC
        "updated"                 -> "2022-08-15T11:30:00Z"
      )
    )
    actual mustBe JsSuccess(expected)
  }

}
