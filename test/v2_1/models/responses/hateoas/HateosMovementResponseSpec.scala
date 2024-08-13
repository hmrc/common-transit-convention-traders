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
import models.common.LocalReferenceNumber
import models.common.MovementReferenceNumber
import models.common.MovementType
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import v2_1.base.TestCommonGenerators
import v2_1.models.responses.MovementSummary

import java.time.OffsetDateTime
import java.time.ZoneOffset

class HateosMovementResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with TestCommonGenerators with OptionValues {

  private val dateTime = OffsetDateTime.of(2022, 8, 15, 11, 45, 0, 0, ZoneOffset.UTC)

  for (movementType <- MovementType.values)
    s"${movementType.movementType} with a valid response, create a valid HateoasMovementSummary" in {
      val movementId = arbitraryMovementId.arbitrary.sample.value

      val response = MovementSummary(
        _id = movementId,
        enrollmentEORINumber = EORINumber("GB123"),
        movementEORINumber = Some(EORINumber("GB456")),
        movementReferenceNumber = Some(MovementReferenceNumber("MRN001")),
        localReferenceNumber = Some(LocalReferenceNumber("LRN001")),
        created = dateTime,
        updated = dateTime
      )

      val actual = HateoasMovementResponse(movementId, response, movementType)

      val expected = Json.obj(
        "_links" -> Json.obj(
          "self"     -> Json.obj("href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}"),
          "messages" -> Json.obj("href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}/messages")
        ),
        "id"                      -> movementId,
        "movementReferenceNumber" -> "MRN001",
        "localReferenceNumber"    -> "LRN001",
        "created"                 -> "2022-08-15T11:45:00Z",
        "updated"                 -> "2022-08-15T11:45:00Z",
        "enrollmentEORINumber"    -> "GB123",
        "movementEORINumber"      -> "GB456"
      )

      actual mustBe expected
    }
}
