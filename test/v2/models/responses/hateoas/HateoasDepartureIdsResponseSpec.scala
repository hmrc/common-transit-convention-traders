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

import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json
import v2.base.CommonGenerators
import v2.models.DepartureId
import v2.models.EORINumber
import v2.models.MovementReferenceNumber
import v2.models.responses.DepartureResponse

import java.time.OffsetDateTime

class HateoasDepartureIdsResponseSpec extends AnyFreeSpec with Matchers with OptionValues with CommonGenerators {

  "DepartureResponses" - {
    "should produce valid Hateoas responses" in {

      val now           = OffsetDateTime.now()
      val enrolmentEori = arbitrary[EORINumber].sample.value

      val departureResponse1 = DepartureResponse(
        _id = arbitrary[DepartureId].sample.value,
        enrollmentEORINumber = enrolmentEori,
        movementEORINumber = arbitrary[EORINumber].sample.value,
        movementReferenceNumber = Some(arbitrary[MovementReferenceNumber].sample.value),
        created = now,
        updated = now
      )

      val departureResponse2 = DepartureResponse(
        _id = arbitrary[DepartureId].sample.value,
        enrollmentEORINumber = enrolmentEori,
        movementEORINumber = arbitrary[EORINumber].sample.value,
        movementReferenceNumber = Some(arbitrary[MovementReferenceNumber].sample.value),
        created = now,
        updated = now
      )
      val responses = Seq(departureResponse1, departureResponse2)

      val expected = Json.obj(
        "_links" -> Json.obj(
          "self" -> Json.obj("href" -> "/customs/transits/movements/departures")
        ),
        "departures" -> responses.map(
          departureResponse =>
            Json.obj(
              "_links" -> Json.obj(
                "self"     -> Json.obj("href" -> s"/customs/transits/movements/departures/${departureResponse._id.value}"),
                "messages" -> s"/customs/transits/movements/departures/${departureResponse._id.value}/messages"
              ),
              "id"                      -> departureResponse._id.value,
              "movementReferenceNumber" -> departureResponse.movementReferenceNumber.value,
              "created"                 -> departureResponse.created,
              "updated"                 -> departureResponse.updated,
              "enrollmentEORINumber"    -> departureResponse.enrollmentEORINumber,
              "movementEORINumber"      -> departureResponse.movementEORINumber
            )
        )
      )

      val actual = HateoasDepartureIdsResponse(responses)

      actual mustBe expected
    }
  }
}
