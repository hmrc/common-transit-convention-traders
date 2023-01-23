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
import v2.base.CommonGenerators
import v2.models.BoxId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.responses.BoxResponse

class HateoasNewMovementResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with OptionValues with CommonGenerators {

  for (movementType <- MovementType.values)
    s"${movementType.movementType} with a valid message response, create a valid HateoasNewMovementResponse" in {
      val movementId = arbitrary[MovementId].sample.value

      val actual = HateoasNewMovementResponse(movementId, None, movementType)

      val expected = Json.obj(
        "_links" -> Json.obj(
          "self" -> Json.obj(
            "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}"
          ),
          "messages" -> Json.obj(
            "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}/messages"
          )
        )
      )

      actual mustBe expected
    }

  for (movementType <- MovementType.values)
    s"${movementType.movementType} with a valid message response, create a valid HateoasNewMovementResponse with BoxId" in {
      val movementId = arbitrary[MovementId].sample.value

      val actual = HateoasNewMovementResponse(movementId, Some(BoxResponse(BoxId("test"))), movementType)

      val expected = Json.obj(
        "_links" -> Json.obj(
          "self" -> Json.obj(
            "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}"
          ),
          "messages" -> Json.obj(
            "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}/messages"
          )
        ),
        "boxId" -> "test"
      )

      actual mustBe expected
    }

}
