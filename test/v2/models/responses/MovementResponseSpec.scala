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

package v2.models.responses

import models.common.MessageId
import models.common.MovementId
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json

class MovementResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  private val gen = Gen.listOfN(10, Gen.alphaChar).map(_.mkString)

  "when MessageID is serialized, return an appropriate JsObject" in forAll(gen, gen) {
    (movement, message) =>
      val actual   = MovementResponse.movementResponseFormat.writes(MovementResponse(MovementId(movement), MessageId(message)))
      val expected = Json.obj("movementId" -> movement, "messageId" -> message)
      actual mustBe expected
  }

  "when an appropriate JsObject is deserialized, return a MessageId" in forAll(gen, gen) {
    (movement, message) =>
      val actual   = MovementResponse.movementResponseFormat.reads(Json.obj("movementId" -> movement, "messageId" -> message))
      val expected = MovementResponse(MovementId(movement), MessageId(message))
      actual mustBe JsSuccess(expected)
  }

}
