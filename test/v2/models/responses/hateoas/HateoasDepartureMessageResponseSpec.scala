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

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import v2.models.MessageId
import v2.models.DepartureId
import v2.models.request.MessageType
import v2.models.responses.MessageResponse

import java.time.OffsetDateTime
import java.time.ZoneOffset

class HateoasDepartureMessageResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  private val hexId    = Gen.listOfN(16, Gen.hexChar).map(_.mkString.toLowerCase)
  private val dateTime = OffsetDateTime.of(2022, 8, 4, 11, 52, 59, 0, ZoneOffset.UTC)

  "with a valid message response, create a valid HateoasDepartureMessageResponse" in {
    val messageId   = MessageId(hexId.sample.get)
    val departureId = DepartureId(hexId.sample.get)
    val triggerId   = MessageId(hexId.sample.get)
    val body        = Gen.alphaNumStr.sample.get
    val response = MessageResponse(
      messageId,
      dateTime,
      dateTime,
      MessageType.DepartureDeclaration,
      Some(triggerId),
      None,
      Some(body)
    )

    val actual = HateoasDepartureMessageResponse(departureId, messageId, response)
    val expected = Json.obj(
      "_links" -> Json.obj(
        "self"      -> Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}/messages/${messageId.value}"),
        "departure" -> Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}")
      ),
      "departureId" -> departureId,
      "messageId"   -> messageId,
      "received"    -> "2022-08-04T11:52:59",
      "messageType" -> MessageType.DepartureDeclaration.code,
      "body"        -> body
    )

    actual mustBe expected
  }

}
