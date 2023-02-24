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

package v2.controllers

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.stubControllerComponents
import v2.base.CommonGenerators
import v2.models.errors.PresentationError

class UpscanResponseParserSpec extends AnyFreeSpec with ScalaFutures with Matchers with ScalaCheckPropertyChecks with CommonGenerators {

  class TestUpscanResponseParserController extends BaseController with UpscanResponseParser with Logging {
    override protected def controllerComponents: ControllerComponents = stubControllerComponents()
  }

  val testController = new TestUpscanResponseParserController()

  "parseUpscanResponse" - {
    "given a successful response in the callback, returns a defined option with value of UploadDetails" in forAll(
      arbitraryUpscanResponse(true).arbitrary
    ) {
      successUpscanResponse =>
        val json = Json.toJson(successUpscanResponse)
        whenReady(testController.parseAndLogUpscanResponse(json).value) {
          either =>
            either mustBe Right(successUpscanResponse)
            either.toOption.get.isSuccess mustBe true
            either.toOption.get.uploadDetails.isDefined mustBe true
        }
    }

    "given a failure response in the callback, returns a defined option with value of FailedDetails" in forAll(
      arbitraryUpscanResponse(false).arbitrary
    ) {
      failureUpscanResponse =>
        val json = Json.toJson(failureUpscanResponse)
        whenReady(testController.parseAndLogUpscanResponse(json).value) {
          either =>
            either mustBe Right(failureUpscanResponse)
            either.toOption.get.isSuccess mustBe false
            either.toOption.get.failureDetails.isDefined mustBe true
        }
    }

    "given a response in the callback that we cannot deserialize, returns a PresentationError" in {
      whenReady(testController.parseAndLogUpscanResponse(Json.obj("reference" -> "abc")).value) {
        either =>
          either mustBe Left(PresentationError.badRequestError("Unexpected Upscan callback response"))
      }
    }
  }

}
