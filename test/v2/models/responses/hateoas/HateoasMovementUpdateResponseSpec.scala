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

import models.common.MovementType
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import play.api.libs.json.Json
import v2.base.TestCommonGenerators

class HateoasMovementUpdateResponseSpec extends AnyFreeSpec with Matchers with OptionValues with TestCommonGenerators {

  for (movementType <- MovementType.values) {

    s"For a small message, with a valid ${movementType.movementType} and message response create a valid HateoasMovementUpdateResponse" in forAll(
      arbitraryMovementId.arbitrary,
      arbitraryMessageId.arbitrary
    ) {
      (movementId, messageId) =>
        val actual = HateoasMovementUpdateResponse(movementId, messageId, movementType, None)
        val expected = Json.obj(
          "_links" -> Json.obj(
            movementType.movementType -> Json.obj(
              "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}"
            ),
            "self" -> Json.obj(
              "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"
            )
          ),
          s"${movementType.movementType}Id" -> movementId.value,
          "messageId"                       -> messageId.value
        )

        actual mustBe expected
    }

    s"For a large message, with a valid ${movementType.movementType} and message response create a valid HateoasMovementUpdateResponse with upscan details" in forAll(
      arbitraryMovementId.arbitrary,
      arbitraryMessageId.arbitrary,
      arbitraryUpscanInitiateResponse.arbitrary
    ) {
      (movementId, messageId, upscanIniateResponse) =>
        val actual = HateoasMovementUpdateResponse(movementId, messageId, movementType, Some(upscanIniateResponse))
        val expected = Json.obj(
          "_links" -> Json.obj(
            movementType.movementType -> Json.obj(
              "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}"
            ),
            "self" -> Json.obj(
              "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"
            )
          ),
          s"${movementType.movementType}Id" -> movementId.value,
          "messageId"                       -> messageId.value,
          "uploadRequest" -> Json.obj(
            "href"   -> upscanIniateResponse.uploadRequest.href,
            "fields" -> upscanIniateResponse.uploadRequest.fields
          )
        )

        actual mustBe expected
    }
  }

}
