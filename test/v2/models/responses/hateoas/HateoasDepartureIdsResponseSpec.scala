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

class HateoasDepartureIdsResponseSpec extends AnyFreeSpec with Matchers with OptionValues with CommonGenerators {

  "A departureId" - {
    "should produce a valid Hateoas response" in {
      val departureId  = arbitrary[DepartureId].sample.value
      val departureId2 = arbitrary[DepartureId].sample.value
      val departureIds = Seq(departureId, departureId2)

      val expected = Json.obj(
        "_links" -> Json.obj(
          "self" -> Json.obj(
            "href" -> "/customs/transits/movements/departures"
          )
        ),
        "departures" -> departureIds.map(
          departureId =>
            Json.obj(
              "_links" -> Json.obj(
                "self" -> Json.obj(
                  "href" -> s"/customs/transits/movements/departures/${departureId.value}"
                ),
                "messages" -> s"/customs/transits/movements/departures/${departureId.value}/messages"
              ),
              "id"                      -> departureId,
              "movementReferenceNumber" -> "ABC123",
              "created"                 -> "20220101 101010Z",
              "updated"                 -> "20220101 101010Z",
              "enrollmentEORINumber"    -> "GB1234567890",
              "movementEORINumber"      -> "GB1234567890"
            )
        )
      )

      val actual = HateoasDepartureIdsResponse(departureIds)

      actual mustBe expected
    }
  }
}
