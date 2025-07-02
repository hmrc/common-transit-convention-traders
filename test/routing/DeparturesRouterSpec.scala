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

package routing

import models.common.MovementType.Departure
import org.apache.pekko.util.Timeout
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.http.MimeTypes
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
import v2_1.base.TestActorSystem
import v2_1.fakes.controllers.FakeV2MovementsController

import scala.concurrent.duration.DurationInt

class DeparturesRouterSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar with TestActorSystem {

  implicit private val timeout: Timeout = 5.seconds

  val sut = new GenericRouting(
    stubControllerComponents(),
    new FakeV2MovementsController
  )

  "route to version 2_1 controller" - {
    "when submitting a departure" - {
      Seq(
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.value)
      ).foreach {
        acceptHeaderValue =>
          val departureHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          )
          s"with accept header set to $acceptHeaderValue" - {

            "must route to the v2 controller and return Accepted when successful" in {
              val request =
                FakeRequest(method = "POST", uri = routes.GenericRouting.createMovement(Departure).url, body = <test></test>, headers = departureHeaders)
              val result = call(sut.createMovement(Departure), request)

              status(result) mustBe ACCEPTED
              contentAsJson(result) mustBe Json.obj("version" -> 2.1) // ensure we get the unique value to verify we called the fake action
            }
          }
      }
    }
    "when getting a single message" - {
      Seq(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value, VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML.value).foreach {
        acceptHeaderValue =>
          s"with accept header set to $acceptHeaderValue (version two)" - {

            val departureHeaders =
              FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeaderValue))

            "must route to the v2 controller and return Ok when successful" in {
              val request = FakeRequest(
                method = "POST",
                uri = routes.GenericRouting.getMessage(Departure, "1234567890abcdef", "1234567890abcdef").url,
                body = <test></test>,
                headers = departureHeaders
              )
              val result = sut.getMessage(Departure, "1234567890abcdef", "1234567890abcdef")(request)

              status(result) mustBe OK
              contentAsJson(result) mustBe Json.obj("version" -> 2.1) // ensure we get the unique value to verify we called the fake action
            }

            "if the departure ID is not the correct format, return a bad request of the appropriate format" in {
              val request = FakeRequest(
                method = "POST",
                uri = routes.GenericRouting.getMessage(Departure, "01", "0123456789abcdef").url,
                body = <test></test>,
                headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeaderValue))
              )
              val result = sut.getMessage(Departure, "01", "01234567890bcdef")(request)

              contentAsJson(result) mustBe Json.obj(
                "code"       -> "BAD_REQUEST",
                "statusCode" -> 400,
                "message"    -> "departureId: Value 01 is not a 16 character hexadecimal string"
              )
              status(result) mustBe BAD_REQUEST

            }

            "if the message ID is not the correct format, return a bad request of the appropriate format" in {
              val request = FakeRequest(
                method = "POST",
                uri = routes.GenericRouting.getMessage(Departure, "0123456789abcdef", "01").url,
                body = <test></test>,
                headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeaderValue))
              )
              val result = sut.getMessage(Departure, "01234567890bcdef", "01")(request)

              status(result) mustBe BAD_REQUEST
              contentAsJson(result) mustBe Json.obj(
                "code"       -> "BAD_REQUEST",
                "statusCode" -> 400,
                "message"    -> "messageId: Value 01 is not a 16 character hexadecimal string"
              )
            }

          }
      }
    }
    "when getting a departure/movement" - {
      Seq(
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.value)
      ).foreach {
        acceptHeaderValue =>
          val departureHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          )
          s"with accept header set to $acceptHeaderValue" - {

            "must route to the v2 controller and return Ok when successful" in {
              val request =
                FakeRequest(method = "GET", body = "", uri = routes.GenericRouting.getMovement(Departure, "1234567890abcdef").url, headers = departureHeaders)
              val result = sut.getMovement(Departure, "1234567890abcdef")(request)

              status(result) mustBe OK
              contentAsJson(result) mustBe Json.obj("version" -> 2.1) // ensure we get the unique value to verify we called the fake action
            }

            "must route to the v2 controller and return BAD_REQUEST when departureId has invalid format" in {
              val request = FakeRequest(method = "GET", body = "", uri = routes.GenericRouting.getMovement(Departure, "").url, headers = departureHeaders)
              val result  = sut.getMovement(Departure, "1234567890abcde")(request)

              status(result) mustBe BAD_REQUEST
              contentAsJson(result) mustBe Json.obj(
                "code"       -> "BAD_REQUEST",
                "statusCode" -> 400,
                "message"    -> "departureId: Value 1234567890abcde is not a 16 character hexadecimal string"
              )
            }
          }

      }
    }
    "when submitting a new message for an existing departure" - {
      Seq(
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.value)
      ).foreach {
        acceptHeaderValue =>
          val departureHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          )
          s"with accept header set to $acceptHeaderValue" - {

            "must route to the v2 controller and return Accepted when successful" in {
              val request =
                FakeRequest(method = "POST", uri = routes.GenericRouting.attachMessage(Departure, "").url, body = <test></test>, headers = departureHeaders)
              val result = call(sut.attachMessage(Departure, "1234567890abcdef"), request)

              status(result) mustBe ACCEPTED
              contentAsJson(result) mustBe Json.obj("version" -> 2.1) // ensure we get the unique value to verify we called the fake action
            }
          }

      }
    }
    "when getting departures for a given enrolment EORI" - {
      Seq(
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.value)
      ).foreach {
        acceptHeaderValue =>
          val departureHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          )
          s"with accept header set to $acceptHeaderValue" - {

            "must route to the v2 controller and return Accepted when successful" in {
              val request =
                FakeRequest(
                  method = "POST",
                  uri = routes.GenericRouting.getMovementForEori(movementType = Departure).url,
                  body = <test></test>,
                  headers = departureHeaders
                )
              val result = call(sut.getMovementForEori(movementType = Departure), request)

              status(result) mustBe OK
              contentAsJson(result) mustBe Json.obj("version" -> 2.1) // ensure we get the unique value to verify we called the fake action
            }

          }
      }
    }
    "when getting departures messages for a given departure" - {
      Seq(
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.value)
      ).foreach {
        acceptHeaderValue =>
          val departureHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          )
          s"with accept header set to $acceptHeaderValue" - {

            "must route to the v2 controller and return OK when successful" in {
              val request = FakeRequest(
                method = "POST",
                uri = routes.GenericRouting.getMessageIds(Departure, "1234567890abcdef").url,
                body = <test></test>,
                headers = departureHeaders
              )
              val result = call(sut.getMessageIds(Departure, "1234567890abcdef", None, None, None, None), request)

              status(result) mustBe OK
              contentAsJson(result) mustBe Json.obj("version" -> 2.1) // ensure we get the unique value to verify we called the fake action

            }
          }
      }
    }
  }
}
