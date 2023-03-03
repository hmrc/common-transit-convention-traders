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

import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import v2.base.TestCommonGenerators
import v2.models.BoxId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.responses.BoxResponse
import v2.models.responses.MovementResponse
import v2.models.responses.UpscanFormTemplate
import v2.models.responses.UpscanInitiateResponse
import v2.models.responses.UpscanReference

class HateoasNewMovementResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with OptionValues with TestCommonGenerators {

  for (movementType <- MovementType.values)
    s"${movementType.movementType} create a valid HateoasNewMovementResponse" - {

      "when the movement response does not contain message Id or box Id" in forAll(
        arbitraryMovementResponse().arbitrary
      ) {
        movementResponse =>
          val actual = HateoasNewMovementResponse(movementResponse, None, None, movementType)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}"
              ),
              "messages" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}/messages"
              )
            )
          )

          actual mustBe expected
      }

      "when the movement response contains BoxId" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val actual = HateoasNewMovementResponse(movementResponse, Some(boxResponse), None, movementType)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}"
              ),
              "messages" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}/messages"
              )
            ),
            "boxId" -> s"${boxResponse.boxId.value}"
          )

          actual mustBe expected
      }

      "with a movement response that contains box ID and an Upload response" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (upscanResponse, movementResponse, boxResponse) =>
          val actual = HateoasNewMovementResponse(movementResponse, Some(boxResponse), Some(upscanResponse), movementType)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}"
              ),
              "messages" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}/messages"
              )
            ),
            "boxId" -> s"${boxResponse.boxId.value}",
            "uploadRequest" -> Json.obj(
              "href"   -> upscanResponse.uploadRequest.href,
              "fields" -> upscanResponse.uploadRequest.fields
            )
          )

          actual mustBe expected
      }
    }

}
