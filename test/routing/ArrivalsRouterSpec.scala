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
import play.api.http.Status.ACCEPTED

import play.api.http.Status.BAD_REQUEST

import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.call
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import v2.base.TestActorSystem
import v2.fakes.controllers.FakeV1ArrivalMessagesController
import v2.fakes.controllers.FakeV1ArrivalsController
import v2.fakes.controllers.FakeV2ArrivalsController

import scala.concurrent.duration.DurationInt

class ArrivalsRouterSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar with TestActorSystem {

  implicit private val timeout: Timeout = 5.seconds

  val sut = new ArrivalsRouter(
    stubControllerComponents(),
    new FakeV1ArrivalsController(),
    new FakeV2ArrivalsController(),
    new FakeV1ArrivalMessagesController()
  )

  "when creating a Arrival Notification" - {
    "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {

      val arrivalsHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml")
      )

      "must route to the v2 controller and return Accepted when successful" in {

        val request =
          FakeRequest(method = "POST", uri = routes.ArrivalsRouter.createArrivalNotification().url, body = <test></test>, headers = arrivalsHeaders)
        val result = call(sut.createArrivalNotification(), request)

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
        val arrivalHeaders = FakeHeaders(acceptHeader ++ Seq(HeaderNames.CONTENT_TYPE -> "application/xml"))
        val withString = acceptHeaderValue
          .getOrElse("nothing")
        s"with accept header set to $withString" - {

          "must route to the v1 controller and return Accepted when successful" in {

            val request =
              FakeRequest(method = "POST", uri = routes.ArrivalsRouter.createArrivalNotification().url, body = <test></test>, headers = arrivalHeaders)
            val result = call(sut.createArrivalNotification(), request)

            status(result) mustBe ACCEPTED
            contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
          }
        }
    }
  }

  "get list of arrival messages with given arrivalId" - {
    "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {

      val acceptHeaderValue = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON))

      "must route to the v2 controller and return Accepted when successful" in {

        val request =
          FakeRequest(method = "GET", body = "", uri = routes.ArrivalsRouter.getArrivalMessageIds("1234567890abcdef").url, headers = acceptHeaderValue)
        val result = sut.getArrivalMessageIds("1234567890abcdef")(request)

        status(result) mustBe ACCEPTED
        contentAsJson(result) mustBe Json.obj("version" -> 2)
      }

      "if the arrival ID is not in correct format, return a bad request" in {
        val request = FakeRequest(
          method = "GET",
          uri = routes.ArrivalsRouter.getArrivalMessageIds("01").url,
          body = "",
          headers = acceptHeaderValue
        )
        val result = sut.getArrivalMessageIds("01")(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"       -> "BAD_REQUEST",
          "statusCode" -> 400,
          "message"    -> "arrivalId: Value 01 is not a 16 character hexadecimal string"
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
        val arrivalHeaders = FakeHeaders(acceptHeader)
        val withString = acceptHeaderValue
          .getOrElse("nothing")
        s"with accept header set to $withString" - {

          "must route to the v1 controller and return Accepted when successful" in {

            val request =
              FakeRequest(method = "GET", uri = routes.ArrivalsRouter.getArrivalMessageIds("123").url, body = "", headers = arrivalHeaders)
            val result = call(sut.getArrivalMessageIds("123"), request)

            status(result) mustBe ACCEPTED
            contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
          }

          "must route to the v1 controller and return 400 with invalid id" in {
            val request = FakeRequest(method = "GET", body = "", uri = routes.ArrivalsRouter.getArrivalMessageIds("").url, headers = arrivalHeaders)
            val result  = sut.getArrivalMessageIds("1234567890abc")(request)

            status(result) mustBe BAD_REQUEST
            contentAsJson(result) mustBe Json.obj(
              "message"    -> "Cannot parse parameter arrivalId as Int: For input string: \"1234567890abc\"",
              "statusCode" -> 400,
              "code"       -> "BAD_REQUEST"
            )
          }
        }
    }
  }

  "when fetching arrivals by EORI " - {

    "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {
      val arrivalsHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)
      )

      "must route to the v2 controller and return Ok when successful" in {
        val request = FakeRequest(method = "GET", body = "", uri = routes.ArrivalsRouter.getArrivalsForEori().url, headers = arrivalsHeaders)
        val result  = call(sut.getArrivalsForEori(), request)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
      }
    }

    "with accept header set to application/vnd.hmrc.1.0+json (version one)" - {
      val arrivalsHeaders = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json"))

      "must route to the v1 controller and return Ok when successful" in {
        val request = FakeRequest(method = "GET", body = "", uri = routes.ArrivalsRouter.getArrivalsForEori().url, headers = arrivalsHeaders)
        val result  = call(sut.getArrivalsForEori(), request)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
      }
    }

  }

}
