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

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import v2.base.TestCommonGenerators
import v2.models.MovementType

class HateoasNewMovementResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with OptionValues with TestCommonGenerators {

  for (movementType <- MovementType.values)
    s"${movementType.movementType} create a valid HateoasNewMovementResponse" - {

      "when the movement response does not contain message Id or box Id" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
          val actual = HateoasNewMovementResponse(movementId, None, None, movementType)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}"
              ),
              "messages" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}/messages"
              )
            ),
            s"${movementType.movementType}Id" -> movementId.value
          )

          actual mustBe expected
      }

      "when the movement response contains BoxId" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val actual = HateoasNewMovementResponse(movementResponse.movementId, Some(boxResponse), None, movementType)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}"
              ),
              "messages" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}/messages"
              )
            ),
            s"${movementType.movementType}Id" -> movementResponse.movementId.value,
            "boxId"                           -> s"${boxResponse.boxId.value}"
          )

          actual mustBe expected
      }

      "with a movement response that contains box ID and an Upload response" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementId.arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (upscanResponse, movementId, boxResponse) =>
          val actual = HateoasNewMovementResponse(movementId, Some(boxResponse), Some(upscanResponse), movementType)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}"
              ),
              "messages" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}/messages"
              )
            ),
            s"${movementType.movementType}Id" -> movementId.value,
            "boxId"                           -> s"${boxResponse.boxId.value}",
            "uploadRequest" -> Json.obj(
              "href"   -> upscanResponse.uploadRequest.href,
              "fields" -> upscanResponse.uploadRequest.fields
            )
          )

          actual mustBe expected
      }

    }

}
