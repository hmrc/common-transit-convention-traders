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
import v2.models.MessageId

class HateoasDepartureUpdateMovementResponseSpec extends AnyFreeSpec with Matchers with OptionValues with CommonGenerators {

  "with a valid departure and message response create a valid HateoasDepartureUpdateMovementResponse" in {
    val departureId = arbitrary[DepartureId].sample.value
    val messageId   = arbitrary[MessageId].sample.value
    val actual      = HateoasDepartureUpdateMovementResponse(departureId, messageId)
    val expected = Json.obj(
      "_links" -> Json.obj(
        "departure" -> Json.obj(
          "href" -> s"/customs/transits/movements/departures/${departureId.value}"
        ),
        "self" -> Json.obj(
          "href" -> s"/customs/transits/movements/departures/${departureId.value}/messages/${messageId.value}"
        )
      )
    )

    actual mustBe expected
  }

}
