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

package utils

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.http.Status
import uk.gov.hmrc.http.HttpResponse

class ResponseHelperSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  class Harness extends ResponseHelper {}

  val clientErrorGenerator: Gen[Int] = Gen.oneOf(Seq(Status.UNAUTHORIZED, Status.FORBIDDEN, Status.NOT_FOUND, Status.BAD_REQUEST))
  val serverErrorGenerator: Gen[Int] = Gen.oneOf(Seq(Status.INTERNAL_SERVER_ERROR, Status.NOT_IMPLEMENTED, Status.SERVICE_UNAVAILABLE, Status.GATEWAY_TIMEOUT))

  "handleNon2xx" - {
    "if status is 4xx and there is a body, returns a status with the body" in {

      forAll(clientErrorGenerator) {
        code =>
          val result = new Harness().handleNon2xx(HttpResponse(code, "testBody"))

          result.body.isKnownEmpty mustBe false
      }
    }

    "if status is 4xx and there is no body, returns a status without a body" in {
      forAll(clientErrorGenerator) {
        code =>
          val result = new Harness().handleNon2xx(HttpResponse(code, null))

          result.body.isKnownEmpty mustBe true
      }
    }

    "if status is not a 4xx, return a simple status code" in {
      forAll(serverErrorGenerator) {
        code =>
          val result = new Harness().handleNon2xx(HttpResponse(code, null))

          result.body.isKnownEmpty mustBe true
      }
    }
  }

}
