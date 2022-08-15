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

package routing

import akka.util.Timeout
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.http.Status.OK
import play.api.http.Status.ACCEPTED
import play.api.http.Status.BAD_REQUEST
import play.api.libs.json.Json
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.call
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import v2.base.TestActorSystem
import v2.fakes.controllers.FakeV1DepartureMessagesController
import v2.fakes.controllers.FakeV1DeparturesController
import v2.fakes.controllers.FakeV2DeparturesController

import scala.concurrent.duration.DurationInt

class DeparturesRouterSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar with TestActorSystem {

  implicit private val timeout: Timeout = 5.seconds

  val sut = new DeparturesRouter(
    stubControllerComponents(),
    new FakeV1DeparturesController,
    new FakeV1DepartureMessagesController,
    new FakeV2DeparturesController
  )

  "when submitting a departure" - {
    // Version 2
    "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {

      val departureHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml")
      )

      "must route to the v2 controller and return Accepted when successful" in {

        val request = FakeRequest(method = "POST", uri = routes.DeparturesRouter.submitDeclaration().url, body = <test></test>, headers = departureHeaders)
        val result  = call(sut.submitDeclaration(), request)

        status(result) mustBe ACCEPTED
        contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
      }

    }

    Seq(None, Some("application/vnd.hmrc.1.0+json"), Some("text/html"), Some("application/vnd.hmrc.1.0+xml"), Some("text/javascript")).foreach {
      acceptHeaderValue =>
        val acceptHeader = acceptHeaderValue
          .map(
            header => Seq(HeaderNames.ACCEPT -> header)
          )
          .getOrElse(Seq.empty)
        val departureHeaders = FakeHeaders(acceptHeader ++ Seq(HeaderNames.CONTENT_TYPE -> "application/xml"))
        val withString = acceptHeaderValue
          .getOrElse("nothing")
        s"with accept header set to $withString" - {

          "must route to the v1 controller and return Accepted when successful" in {

            val request = FakeRequest(method = "POST", uri = routes.DeparturesRouter.submitDeclaration().url, body = <test></test>, headers = departureHeaders)
            val result  = call(sut.submitDeclaration(), request)

            status(result) mustBe ACCEPTED
            contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
          }
        }

    }
  }

  "when getting a single message" - {

    "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {

      val departureHeaders = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml"))

      "must route to the v2 controller and return Accepted when successful" in {

        val request = FakeRequest(method = "POST", uri = routes.DeparturesRouter.submitDeclaration().url, body = <test></test>, headers = departureHeaders)
        val result  = sut.getMessage("1234567890abcdef", "1234567890abcdef")(request)

        status(result) mustBe ACCEPTED
        contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
      }

      "if the departure ID is not the correct format, return a bad request of the appropriate format" in {
        val request = FakeRequest(
          method = "POST",
          uri = routes.DeparturesRouter.getMessage("01", "0123456789abcdef").url,
          body = <test></test>,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml"))
        )
        val result = sut.getMessage("01", "01234567890bcdef")(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"       -> "BAD_REQUEST",
          "statusCode" -> 400,
          "message"    -> "departureId: Value 01 is not a 16 character hexadecimal string"
        )
      }

      "if the message ID is not the correct format, return a bad request of the appropriate format" in {
        val request = FakeRequest(
          method = "POST",
          uri = routes.DeparturesRouter.getMessage("0123456789abcdef", "01").url,
          body = <test></test>,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml"))
        )
        val result = sut.getMessage("01234567890bcdef", "01")(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"       -> "BAD_REQUEST",
          "statusCode" -> 400,
          "message"    -> "messageId: Value 01 is not a 16 character hexadecimal string"
        )
      }

    }

    Seq(None, Some("application/vnd.hmrc.1.0+json"), Some("text/html"), Some("application/vnd.hmrc.1.0+xml"), Some("text/javascript")).foreach {
      acceptHeaderValue =>
        val acceptHeader = acceptHeaderValue
          .map(
            header => Seq(HeaderNames.ACCEPT -> header)
          )
          .getOrElse(Seq.empty)
        val departureHeaders = FakeHeaders(acceptHeader ++ Seq(HeaderNames.CONTENT_TYPE -> "application/xml"))
        val withString = acceptHeaderValue
          .getOrElse("nothing")
        s"with accept header set to $withString" - {

          "must route to the v1 controller and return Accepted when successful" in {

            val request =
              FakeRequest(method = "POST", uri = routes.DeparturesRouter.getMessage("123", "4").url, body = <test></test>, headers = departureHeaders)
            val result = sut.getMessage("123", "4")(request)

            status(result) mustBe ACCEPTED
            contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
          }
        }

    }

    "with accept header set to \"application/vnd.hmrc.1.0+json\" and incorrect IDs" - {
      "if the departure ID is not the correct format, return a bad request of the appropriate format" in {
        val request = FakeRequest(
          method = "POST",
          uri = routes.DeparturesRouter.getMessage("a", "1").url,
          body = <test></test>,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json", HeaderNames.CONTENT_TYPE -> "application/xml"))
        )
        val result = sut.getMessage("a", "1")(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"       -> "BAD_REQUEST",
          "statusCode" -> 400,
          "message"    -> "Cannot parse parameter departureId as Int: For input string: \"a\""
        )
      }

      "if the message ID is not the correct format, return a bad request of the appropriate format" in {
        val request = FakeRequest(
          method = "POST",
          uri = routes.DeparturesRouter.getMessage("1", "a").url,
          body = <test></test>,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json", HeaderNames.CONTENT_TYPE -> "application/xml"))
        )
        val result = sut.getMessage("1", "a")(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"       -> "BAD_REQUEST",
          "statusCode" -> 400,
          "message"    -> "Cannot parse parameter messageId as Int: For input string: \"a\""
        )
      }
    }

  }

  "when getting a departure/movement" - {
    "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {
      val departureHeaders = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"))

      "must route to the v2 controller and return Ok when successful" in {
        val request = FakeRequest(method = "GET", body = "", uri = routes.DeparturesRouter.getDeparture("").url, headers = departureHeaders)
        val result  = sut.getDeparture("1234567890abcdef")(request)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
      }

      "must route to the v2 controller and return BAD_REQUEST when departureId has invalid format" in {
        val request = FakeRequest(method = "GET", body = "", uri = routes.DeparturesRouter.getDeparture("").url, headers = departureHeaders)
        val result  = sut.getDeparture("1234567890abcde")(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"       -> "BAD_REQUEST",
          "statusCode" -> 400,
          "message"    -> "departureId: Value 1234567890abcde is not a 16 character hexadecimal string"
        )
      }

    }

    "with accept header set to application/vnd.hmrc.1.0+json (version one)" - {
      val departureHeaders = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json"))

      "must route to the v1 controller and return Ok when successful" in {
        val request = FakeRequest(method = "GET", body = "", uri = routes.DeparturesRouter.getDeparture("").url, headers = departureHeaders)
        val result  = sut.getDeparture("1234567890")(request)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
      }

      "must route to the v1 controller by default if version unspecified" in {
        val request = FakeRequest(method = "GET", body = "", uri = routes.DeparturesRouter.getDeparture("").url, headers = FakeHeaders())
        val result  = sut.getDeparture("1234567890")(request)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.obj("version" -> 1)
      }

      "must route to the v1 controller and return 400 with invalid id" in {
        val request = FakeRequest(method = "GET", body = "", uri = routes.DeparturesRouter.getDeparture("").url, headers = departureHeaders)
        val result  = sut.getDeparture("1234567890abc")(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "message"    -> "Cannot parse parameter departureId as Int: For input string: \"1234567890abc\"",
          "statusCode" -> 400,
          "code"       -> "BAD_REQUEST"
        )
      }
    }
  }
}
