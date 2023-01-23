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
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.JsString
import play.api.libs.json.Json
import v2.base.CommonGenerators
import v2.models.JsonPayload
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.XmlPayload
import v2.models.request.MessageType
import v2.models.responses.MessageSummary

import java.time.OffsetDateTime
import java.time.ZoneOffset

class HateoasMovementMessageResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with CommonGenerators with OptionValues {

  private val dateTime = OffsetDateTime.of(2022, 8, 4, 11, 52, 59, 0, ZoneOffset.UTC)

  for (movementType <- MovementType.values) {
    s"when the MessageSummary contains a ${movementType.movementType} json payload, create a valid HateoasMovementMessageResponse with the body as a JsValue" in {
      val messageId   = arbitrary[MessageId].sample.value
      val departureId = arbitrary[MovementId].sample.value
      val json        = Json.obj("test" -> "body")
      val jsonString  = Json.stringify(json)

      val response = MessageSummary(
        messageId,
        dateTime,
        MessageType.DeclarationData,
        Some(JsonPayload(jsonString))
      )

      val actual = HateoasMovementMessageResponse(departureId, messageId, response, movementType)
      val expected = Json.obj(
        "_links" -> Json.obj(
          "self"                    -> Json.obj("href" -> s"/customs/transits/movements/${movementType.urlFragment}/${departureId.value}/messages/${messageId.value}"),
          movementType.movementType -> Json.obj("href" -> s"/customs/transits/movements/${movementType.urlFragment}/${departureId.value}")
        ),
        "id"                              -> messageId.value,
        s"${movementType.movementType}Id" -> departureId.value,
        "received"                        -> "2022-08-04T11:52:59Z",
        "type"                            -> MessageType.DeclarationData.code,
        "body"                            -> json
      )

      actual mustBe expected
    }

    s"when the MessageSummary contains a ${movementType.movementType} xml payload, create a valid HateoasMovementMessageResponse with the body as a JsValue" in {
      val messageId  = arbitrary[MessageId].sample.value
      val movementId = arbitrary[MovementId].sample.value
      val body       = Gen.alphaNumStr.sample.value
      val response = MessageSummary(
        messageId,
        dateTime,
        MessageType.DeclarationData,
        Some(XmlPayload(body))
      )

      val actual = HateoasMovementMessageResponse(movementId, messageId, response, movementType)
      val expected = Json.obj(
        "_links" -> Json.obj(
          "self"                    -> Json.obj("href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"),
          movementType.movementType -> Json.obj("href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}")
        ),
        "id"                              -> messageId.value,
        s"${movementType.movementType}Id" -> movementId.value,
        "received"                        -> "2022-08-04T11:52:59Z",
        "type"                            -> MessageType.DeclarationData.code,
        "body"                            -> JsString(body)
      )

      actual mustBe expected
    }
  }
}
