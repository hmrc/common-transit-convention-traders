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
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import v2.base.CommonGenerators
import v2.models.MovementType

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class HateoasMovementIdsResponseSpec extends AnyFreeSpec with Matchers with OptionValues with CommonGenerators with ScalaCheckDrivenPropertyChecks {

  for (movementType <- MovementType.values)
    s"${movementType.movementType} should produce valid HateoasMovementIdsResponse responses" in forAll(Gen.option(arbitrary[OffsetDateTime])) {
      updatedSince =>
        val departureResponse1 = arbitraryMovementSummary.arbitrary.sample.value
        val departureResponse2 = arbitraryMovementSummary.arbitrary.sample.value

        val responses = Seq(departureResponse1, departureResponse2)
        val expectedQueryString = updatedSince
          .map(
            date => s"?updatedSince=${DateTimeFormatter.ISO_DATE_TIME.format(date)}"
          )
          .getOrElse("")

        val expected = Json.obj(
          "_links" -> Json.obj(
            "self" -> Json.obj("href" -> s"/customs/transits/movements/${movementType.urlFragment}$expectedQueryString")
          ),
          movementType.urlFragment -> responses.map(
            movementResponse =>
              Json.obj(
                "_links" -> Json.obj(
                  "self"     -> Json.obj("href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse._id.value}"),
                  "messages" -> Json.obj("href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse._id.value}/messages")
                ),
                "id"                      -> movementResponse._id.value,
                "movementReferenceNumber" -> movementResponse.movementReferenceNumber.value,
                "created"                 -> movementResponse.created,
                "updated"                 -> movementResponse.updated,
                "enrollmentEORINumber"    -> movementResponse.enrollmentEORINumber,
                "movementEORINumber"      -> movementResponse.movementEORINumber
              )
          )
        )

        val actual = HateoasMovementIdsResponse(responses, movementType, updatedSince)

        actual mustBe expected
    }

}
