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

package v2_1.services

import cats.implicits.catsSyntaxEitherId
import models.common.errors.ExtractionError
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import v2_1.base.StreamTestHelpers
import v2_1.base.TestActorSystem
import v2_1.models.request.MessageType

import scala.concurrent.ExecutionContext.Implicits.global

class JsonMessageParsingServiceSpec
    extends AnyFreeSpec
    with TestActorSystem
    with Matchers
    with StreamTestHelpers
    with ScalaFutures
    with ScalaCheckPropertyChecks {

  val validJson: JsObject =
    Json.obj(
      "n1:CC013C" ->
        Json.obj(
          "messageType" -> "CC013C",
          "HolderOfTheTransitProcedure" ->
            Json.obj("identificationNumber" -> "GB1234")
        )
    )

  val invalidMessageType: JsObject =
    Json.obj(
      "n1:CC015C" ->
        Json.obj(
          "messageType" -> "CC015C"
        )
    )

  val withInvalidRootNodeEntry: JsObject =
    Json.obj(
      "n1CC013C" ->
        Json.obj(
          "HolderOfTheTransitProcedure" ->
            Json.obj("identificationNumber" -> "GB1234")
        )
    )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "extractMessageType and then" - {
    "if it is valid, return an appropriate Message Type" in {
      val jsonParsingService = new JsonMessageParsingServiceImpl()
      val payload            = createStream(validJson)
      val response =
        jsonParsingService.extractMessageType(payload, MessageType.updateMessageTypesSentByDepartureTrader)

      whenReady(response.value) {
        res => res mustBe MessageType.DeclarationAmendment.asRight
      }
    }

    "if it doesn't have a valid message type, return ExtractionError.MessageTypeNotFound" in {
      val jsonParsingService = new JsonMessageParsingServiceImpl()
      val payload            = createStream(invalidMessageType)
      val response =
        jsonParsingService.extractMessageType(payload, MessageType.updateMessageTypesSentByDepartureTrader)

      whenReady(response.value) {
        res =>
          res mustBe ExtractionError.MessageTypeNotFound("CC015C").asLeft
      }
    }

    "if the root node is invalid, return ExtractionError.MalformedInput" in {
      val jsonParsingService = new JsonMessageParsingServiceImpl()
      val payload            = createStream(withInvalidRootNodeEntry)
      val response =
        jsonParsingService.extractMessageType(payload, MessageType.updateMessageTypesSentByDepartureTrader)

      whenReady(response.value) {
        res =>
          res mustBe ExtractionError.MalformedInput.asLeft
      }
    }
  }
}
