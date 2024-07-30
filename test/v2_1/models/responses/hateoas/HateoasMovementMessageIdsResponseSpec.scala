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

import models.common.ItemCount
import models.common.MovementId
import models.common.MovementType
import models.common.PageNumber
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2_1.base.TestCommonGenerators
import v2_1.models.MessageStatus
import v2_1.models.TotalCount
import v2_1.models.responses.MessageSummary
import v2_1.models.responses.PaginationMessageSummary

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class HateoasMovementMessageIdsResponseSpec
    extends AnyFreeSpec
    with HateoasResponse
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with OptionValues
    with TestCommonGenerators {

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
          val processingResponse = Seq(response.copy(status = Some(MessageStatus.Processing)))
          val responses          = PaginationMessageSummary(TotalCount(processingResponse.length.toLong), processingResponse)

          val actual = HateoasMovementMessageIdsResponse(departureId, responses, dateTime, MovementType.Departure, None, None, None)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self"      -> selfUrl(departureId, dateTime, None, None, None),
              "departure" -> Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}")
            ),
            "totalCount" -> responses.totalCount.value,
            "messages" -> responses.messageSummary.map(
              response =>
                Json.obj(
                  "_links" -> Json.obj(
                    "self"      -> Json.obj("href" -> getMessageUri(departureId, response.id, MovementType.Departure)),
                    "departure" -> Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}")
                  ),
                  "id"          -> response.id.value,
                  "departureId" -> departureId.value,
                  "received"    -> response.received,
                  "type"        -> response.messageType.get.code,
                  "status"      -> response.status
                )
            )
          )

          actual mustBe expected
      }

      s"with a valid message response and receivedSince $set and MessageStatus is Pending, create a valid HateoasMovementMessageIdsResponse without type" in forAll(
        arbitrary[MovementId],
        arbitrary[MessageSummary],
        Gen.option(arbitrary[PageNumber]),
        Gen.option(arbitrary[ItemCount]),
        Gen.option(arbitrary[OffsetDateTime])
      ) {
        (departureId, response, page, count, receivedUntil) =>
          val pendingResponses = Seq(response.copy(status = Some(MessageStatus.Pending)))
          val responses        = PaginationMessageSummary(TotalCount(pendingResponses.length.toLong), pendingResponses)

          val actual = HateoasMovementMessageIdsResponse(departureId, responses, dateTime, MovementType.Departure, page, count, receivedUntil)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self"      -> selfUrl(departureId, dateTime, page, count, receivedUntil),
              "departure" -> Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}")
            ),
            "totalCount" -> responses.totalCount,
            "messages" -> responses.messageSummary.map(
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

  private def selfUrl(
    departureId: MovementId,
    dateTime: Option[OffsetDateTime],
    page: Option[PageNumber],
    count: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime]
  ): JsObject = {
    val time: String     = dateTime.fold("")("receivedSince=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(_))
    val pageNum: String  = page.fold("")("page=" + _.value)
    val countNum: String = count.fold("")("count=" + _.value)
    val received: String = receivedUntil.fold("")("receivedUntil=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(_))

    val queryString = Seq(time, pageNum, countNum, received)
      .map(
        param => if (param.length > 0) "&" + param else ""
      )
      .mkString
      .replaceFirst("&", "?")

    Json.obj(
      "href" -> { "/customs/transits/movements/departures/" + departureId.value + "/messages" + queryString }
    )

  }
}
