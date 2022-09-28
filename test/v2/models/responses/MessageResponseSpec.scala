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

package v2.models.responses

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import v2.models.MessageId
import v2.models.request.MessageType

import java.time.OffsetDateTime
import java.time.ZoneOffset

class MessageResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  private val hexId    = Gen.listOfN(16, Gen.hexChar).map(_.mkString.toLowerCase)
  private val dateTime = OffsetDateTime.of(2022, 8, 4, 9, 59, 26, 0, ZoneOffset.UTC)

  "when MessageResponse is serialized, return an appropriate JsObject" in {
    val messageId = MessageId(hexId.sample.get)
    val triggerId = MessageId(hexId.sample.get)
    val body      = Gen.alphaNumStr.sample.get
    val actual = MessageResponse.messageResponseFormat.writes(
      MessageResponse(
        messageId,
        dateTime,
        dateTime,
        MessageType.DeclarationData,
        Some(triggerId),
        None,
        Some(body)
      )
    )
    val expected = Json.obj(
      "id"          -> messageId,
      "received"    -> "2022-08-04T09:59:26Z", // due to OffsetDateTime in UTC
      "generated"   -> "2022-08-04T09:59:26Z",
      "messageType" -> "IE015",
      "triggerId"   -> triggerId,
      "body"        -> body
    )
    actual mustBe expected
  }

  "when an appropriate JsObject is deserialized, return a MessageResponse" in {
    val messageId = MessageId(hexId.sample.get)
    val triggerId = MessageId(hexId.sample.get)
    val body      = Gen.alphaNumStr.sample.get
    val expected =
      MessageResponse(
        messageId,
        dateTime,
        dateTime,
        MessageType.DeclarationData,
        Some(triggerId),
        None,
        Some(body)
      )
    val actual = MessageResponse.messageResponseFormat.reads(
      Json.obj(
        "id"          -> messageId,
        "received"    -> "2022-08-04T09:59:26Z",
        "generated"   -> "2022-08-04T09:59:26Z",
        "messageType" -> "IE015",
        "triggerId"   -> triggerId,
        "body"        -> body
      )
    )
    actual mustBe JsSuccess(expected)
  }

}
