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

package v2.models.errors

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.http.UpstreamErrorResponse

class PresentationErrorSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  "Test Json is as expected" - {
    def testStandard(function: String => PresentationError, message: String, code: String) = {
      val sut    = function(message)
      val result = Json.toJson(sut)

      result mustBe Json.obj("message" -> message, "code" -> code)
    }

    "for Forbidden" in testStandard(PresentationError.forbiddenError, "forbidden", "FORBIDDEN")

    "for EntityTooLarge" in testStandard(PresentationError.entityTooLargeError, "entity too large", "REQUEST_ENTITY_TOO_LARGE")

    "for UnsupportedMediaType" in testStandard(PresentationError.unsupportedMediaTypeError, "unsupported media type", "UNSUPPORTED_MEDIA_TYPE")

    "for BadRequest" in testStandard(PresentationError.badRequestError, "bad request", "BAD_REQUEST")

    "for NotFound" in testStandard(PresentationError.notFoundError, "not found", "NOT_FOUND")

    Seq(Some(new IllegalStateException("message")), None).foreach {
      exception =>
        val textFragment = exception
          .map(
            _ => "contains"
          )
          .getOrElse("does not contain")
        s"for an unexpected error that $textFragment a Throwable" in {
          // Given this exception
          val exception = new IllegalStateException("message")

          // when we create a error for this
          val sut = PresentationError.internalServiceError(cause = Some(exception))

          // and when we turn it to Json
          val json = Json.toJson(sut)

          // then we should get an expected output
          json mustBe Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "Internal server error")
        }
    }

    "for a binding bad request" in {
      val gen          = Gen.alphaNumStr.sample.getOrElse("something")
      val bindingError = PresentationError.bindingBadRequestError(gen)

      val json = Json.toJson(bindingError)

      json mustBe Json.obj(
        "code"       -> "BAD_REQUEST",
        "statusCode" -> 400,
        "message"    -> gen
      )
    }

    "for an upstream error" in {
      // Given this upstream error
      val upstreamErrorResponse = UpstreamErrorResponse("error", 500)

      // when we create a error for this
      val sut = UpstreamServiceError.causedBy(upstreamErrorResponse)

      // and when we turn it to Json
      val json = Json.toJson(sut)

      // then we should get an expected output
      json mustBe Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "Internal server error")
    }
  }

}
