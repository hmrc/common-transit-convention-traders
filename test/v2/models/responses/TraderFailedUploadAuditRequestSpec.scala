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

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import v2.base.TestCommonGenerators
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId

class TraderFailedUploadAuditRequestSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with TestCommonGenerators {

  private val gen = Gen.listOfN(10, Gen.alphaChar).map(_.mkString)

  "when request is serialized, return an appropriate JsObject" in forAll(gen, gen, gen, arbitraryMovementType.arbitrary) {
    (movementId, messageId, eoriNumber, movementType) =>
      val actual = TraderFailedUploadAuditRequest.traderFailedUploadAuditResponseFormat.writes(
        TraderFailedUploadAuditRequest(MovementId(movementId), MessageId(messageId), EORINumber(eoriNumber), movementType)
      )
      val expected = Json.obj("movementId" -> movementId, "messageId" -> messageId, "enrollmentEORINumber" -> eoriNumber, "movementType" -> movementType)
      actual mustBe expected
  }

  "when an appropriate JsObject is deserialized, return a request" in forAll(gen, gen, gen, arbitraryMovementType.arbitrary) {
    (movementId, messageId, eoriNumber, movementType) =>
      val actual = TraderFailedUploadAuditRequest.traderFailedUploadAuditResponseFormat.reads(
        Json.obj("movementId" -> movementId, "messageId" -> messageId, "enrollmentEORINumber" -> eoriNumber, "movementType" -> movementType)
      )
      val expected = TraderFailedUploadAuditRequest(MovementId(movementId), MessageId(messageId), EORINumber(eoriNumber), movementType)
      actual mustBe JsSuccess(expected)
  }

}
