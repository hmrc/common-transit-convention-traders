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
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.base.CommonGenerators
import v2.models.MessageStatus
import v2.models.MovementId
import v2.models.MovementType
import v2.models.responses.MessageSummary

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class HateoasMovementMessageIdsResponseSpec
    extends AnyFreeSpec
    with HateoasResponse
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with OptionValues
    with CommonGenerators {

  Seq(arbitrary[OffsetDateTime].sample, None).foreach {
    dateTime =>
      val set = dateTime
        .map(
          _ => "set"
        )
        .getOrElse("not set")

      s"with a valid message response and receivedSince $set and MessageStatus is not Pending, create a valid HateoasMovementMessageIdsResponse with type" in forAll(
        arbitrary[MovementId],
        arbitrary[MessageSummary]
      ) {
        (departureId, response) =>
          val responses = Seq(response.copy(status = Some(MessageStatus.Processing)))

          val actual = HateoasMovementMessageIdsResponse(departureId, responses, dateTime, MovementType.Departure)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self"      -> selfUrl(departureId, dateTime),
              "departure" -> Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}")
            ),
            "messages" -> responses.map(
              response =>
                Json.obj(
                  "_links" -> Json.obj(
                    "self"      -> Json.obj("href" -> getMessageUri(departureId, response.id, MovementType.Departure)),
                    "departure" -> Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}")
                  ),
                  "id"          -> response.id.value,
                  "departureId" -> departureId.value,
                  "received"    -> response.received,
                  "type"        -> response.messageType.code,
                  "status"      -> response.status
                )
            )
          )

          actual mustBe expected
      }

      s"with a valid message response and receivedSince $set and MessageStatus is Pending, create a valid HateoasMovementMessageIdsResponse without type" in forAll(
        arbitrary[MovementId],
        arbitrary[MessageSummary]
      ) {
        (departureId, response) =>
          val responses = Seq(response.copy(status = Some(MessageStatus.Pending)))
          val actual    = HateoasMovementMessageIdsResponse(departureId, responses, dateTime, MovementType.Departure)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self"      -> selfUrl(departureId, dateTime),
              "departure" -> Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}")
            ),
            "messages" -> responses.map(
              response =>
                Json.obj(
                  "_links" -> Json.obj(
                    "self"      -> Json.obj("href" -> getMessageUri(departureId, response.id, MovementType.Departure)),
                    "departure" -> Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}")
                  ),
                  "id"          -> response.id.value,
                  "departureId" -> departureId.value,
                  "received"    -> response.received,
                  "status"      -> response.status
                )
            )
          )

          actual mustBe expected
      }
  }

  private def selfUrl(departureId: MovementId, dateTime: Option[OffsetDateTime]): JsObject = dateTime match {
    case Some(odt) =>
      val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(odt)
      Json.obj(
        "href" -> s"/customs/transits/movements/departures/${departureId.value}/messages?receivedSince=$time"
      )
    case None => Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}/messages")
  }

}
