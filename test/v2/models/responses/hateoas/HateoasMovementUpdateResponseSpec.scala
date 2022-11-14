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
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType

class HateoasMovementUpdateResponseSpec extends AnyFreeSpec with Matchers with OptionValues with CommonGenerators {

  for (movementType <- MovementType.values)
    s"with a valid ${movementType.movementType} and message response create a valid HateoasMovementUpdateResponse" in {
      val movementId = arbitrary[MovementId].sample.value
      val messageId  = arbitrary[MessageId].sample.value
      val actual     = HateoasMovementUpdateResponse(movementId, messageId, movementType)
      val expected = Json.obj(
        "_links" -> Json.obj(
          movementType.movementType -> Json.obj(
            "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}"
          ),
          "self" -> Json.obj(
            "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"
          )
        )
      )

      actual mustBe expected
    }

}