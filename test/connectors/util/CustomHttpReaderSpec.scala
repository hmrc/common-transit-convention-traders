/*
 * Copyright 2021 HM Revenue & Customs
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

package connectors.util

import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.http.Status
import uk.gov.hmrc.http.HttpResponse

class CustomHttpReaderSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar with ScalaCheckPropertyChecks {

  val _2xxGenerator: Gen[Int]        = Gen.oneOf(Seq(Status.OK, Status.CREATED, Status.ACCEPTED, Status.NO_CONTENT))
  val clientErrorGenerator: Gen[Int] = Gen.oneOf(Seq(Status.UNAUTHORIZED, Status.FORBIDDEN, Status.NOT_FOUND, Status.BAD_REQUEST))
  val serverErrorGenerator: Gen[Int] = Gen.oneOf(Seq(Status.INTERNAL_SERVER_ERROR, Status.NOT_IMPLEMENTED, Status.SERVICE_UNAVAILABLE, Status.GATEWAY_TIMEOUT))

  def sut(status: Int) = CustomHttpReader.read("POST", "abc", HttpResponse(status, ""))

  "must convert LOCKED to INTERNAL_SERVER_ERROR" in {
    sut(Status.LOCKED).status mustEqual Status.INTERNAL_SERVER_ERROR
  }

  "must convert BAD_GATEWAY to INTERNAL_SERVER_ERROR" in {
    sut(Status.BAD_GATEWAY).status mustEqual Status.INTERNAL_SERVER_ERROR
  }

  "must leave other error types alone" in {
    forAll(clientErrorGenerator) {
      code =>
        sut(code).status mustEqual code
    }
    forAll(serverErrorGenerator) {
      code =>
        sut(code).status mustEqual code
    }
  }

  "must leave 2xx codes alone" in {
    forAll(_2xxGenerator) {
      code =>
        sut(code).status mustEqual code
    }
  }
}
