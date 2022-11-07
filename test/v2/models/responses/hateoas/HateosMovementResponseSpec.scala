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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import v2.models.MovementType
import v2.utils.CommonGenerators

class HateosMovementResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with CommonGenerators {

  for (movementType <- Seq(MovementType.Arrival, MovementType.Departure))
    s"with a valid ${movementType.movementType} response, create a valid HateoasDepartureResponse" in {
      val movementResponse = arbitraryMovementResponse.arbitrary.sample.get

      val actual      = HateoasMovementResponse(movementResponse._id, movementResponse, movementType)
      val selfUri     = s"/customs/transits/movements/departures/${movementResponse._id}"
      val messagesUri = s"/customs/transits/movements/departures/${movementResponse._id}/messages"

      val expected = Json.obj(
        "_links" -> Json.obj(
          "self"     -> Json.obj("href" -> selfUri),
          "messages" -> Json.obj("href" -> messagesUri)
        ),
        "id"                      -> movementResponse._id.value,
        "movementReferenceNumber" -> movementResponse.movementReferenceNumber.get.value,
        "created"                 -> movementResponse.created.toString,
        "updated"                 -> movementResponse.created.toString,
        "enrollmentEORINumber"    -> movementResponse.enrollmentEORINumber.value,
        "movementEORINumber"      -> movementResponse.movementReferenceNumber.get
      )

      actual mustBe expected
    }
}
