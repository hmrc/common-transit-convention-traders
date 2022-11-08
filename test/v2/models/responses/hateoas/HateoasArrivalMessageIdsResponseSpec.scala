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
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.base.CommonGenerators
import v2.models.MovementId
import v2.models.responses.MessageSummary

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class HateoasArrivalMessageIdsResponseSpec
    extends AnyFreeSpec
    with HateoasResponse
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with OptionValues
    with CommonGenerators {

  "with a valid message response and receivedSince, create a valid HateoasArrivalMessageIdsResponse" in forAll(
    arbitrary[MovementId],
    Gen.option(arbitrary[OffsetDateTime]),
    Gen.listOfN(3, arbitrary[MessageSummary])
  ) {
    (arrivalId, receivedSince, responses) =>
      val actual = HateoasArrivalMessageIdsResponse(arrivalId, responses, receivedSince)

      val expected = Json.obj(
        "_links" -> Json.obj(
          "self"    -> selfUrl(arrivalId, receivedSince),
          "arrival" -> Json.obj("href" -> s"/customs/transits/movements/arrivals/${arrivalId.value}")
        ),
        "messages" -> responses.map(
          response =>
            Json.obj(
              "_links" -> Json.obj(
                "self"    -> Json.obj("href" -> arrivalMessageUri(arrivalId, response.id)),
                "arrival" -> Json.obj("href" -> s"/customs/transits/movements/arrivals/${arrivalId.value}")
              ),
              "id"        -> response.id.value,
              "arrivalId" -> arrivalId.value,
              "received"  -> response.received,
              "type"      -> response.messageType.code
            )
        )
      )

      actual mustBe expected
  }

  private def selfUrl(arrivalId: MovementId, dateTime: Option[OffsetDateTime]): JsObject = dateTime match {
    case Some(odt) =>
      val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(odt)
      Json.obj(
        "href" -> s"/customs/transits/movements/arrivals/${arrivalId.value}/messages?receivedSince=$time"
      )
    case None => Json.obj("href" -> s"/customs/transits/movements/arrivals/${arrivalId.value}/messages")
  }

}
