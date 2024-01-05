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

import org.apache.pekko.util.Timeout
import config.AppConfig
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.GONE
import play.api.http.Status.NOT_ACCEPTABLE
import play.api.http.Status.OK
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
import v2.fakes.controllers.FakeV2MovementsController

import scala.concurrent.duration.DurationInt

class DeparturesRouterSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar with TestActorSystem {

  implicit private val timeout: Timeout = 5.seconds

  val mockAppConfig = mock[AppConfig]

  val sut = new DeparturesRouter(
    stubControllerComponents(),
    new FakeV1DeparturesController,
    new FakeV1DepartureMessagesController,
    new FakeV2MovementsController,
    mockAppConfig
  )

  "when submitting a departure" - {
    // Version 2
    Seq(
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON),
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML),
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN)
    ).foreach {
      acceptHeaderValue =>
        val departureHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
        )
        s"with accept header set to $acceptHeaderValue" - {

          "must route to the v2 controller and return Accepted when successful" in {

            when(mockAppConfig.enablePhase5).thenReturn(true)

            val request = FakeRequest(method = "POST", uri = routes.DeparturesRouter.submitDeclaration().url, body = <test></test>, headers = departureHeaders)
            val result  = call(sut.submitDeclaration(), request)

            status(result) mustBe ACCEPTED
            contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
          }

          "must route to the v2 controller and return NotAcceptable when phase5 feature is disabled" in {

            when(mockAppConfig.enablePhase5).thenReturn(false)

            val request = FakeRequest(method = "POST", uri = routes.DeparturesRouter.submitDeclaration().url, body = <test></test>, headers = departureHeaders)
            val result  = call(sut.submitDeclaration(), request)

            status(result) mustBe NOT_ACCEPTABLE
            contentAsJson(result) mustBe Json.obj(
              "message" -> "CTC Traders API version 2 is not yet available. Please continue to use version 1 to submit transit messages.",
              "code"    -> "NOT_ACCEPTABLE"
            )
          }
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

            when(mockAppConfig.disablePhase4).thenReturn(false)

            val request = FakeRequest(method = "POST", uri = routes.DeparturesRouter.submitDeclaration().url, body = <test></test>, headers = departureHeaders)
            val result  = call(sut.submitDeclaration(), request)

            status(result) mustBe ACCEPTED
            contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
          }

          "must route to the v1 controller and return Gone when phase5 feature is enabled" in {

            when(mockAppConfig.disablePhase4).thenReturn(true)

            val request = FakeRequest(method = "POST", uri = routes.DeparturesRouter.submitDeclaration().url, body = <test></test>, headers = departureHeaders)
            val result  = call(sut.submitDeclaration(), request)

            status(result) mustBe GONE
            contentAsJson(result) mustBe Json.obj(
              "message" -> "New NCTS4 Departure Declarations can no longer be created using CTC Traders API v1.0. Use CTC Traders API v2.0 to create new NCTS5 Departure Declarations.",
              "code"    -> "GONE"
            )
          }
        }

    }
  }

  "when getting a single message" - {

    Seq(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML).foreach {
      acceptHeaderValue =>
        s"with accept header set to $acceptHeaderValue (version two)" - {

          val departureHeaders =
            FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeaderValue))

          "must route to the v2 controller and return Ok when successful" in {
            when(mockAppConfig.enablePhase5).thenReturn(true)
            val request = FakeRequest(
              method = "POST",
              uri = routes.DeparturesRouter.getMessage("1234567890abcdef", "1234567890abcdef").url,
              body = <test></test>,
              headers = departureHeaders
            )
            val result = sut.getMessage("1234567890abcdef", "1234567890abcdef")(request)

            status(result) mustBe OK
            contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
          }

          "if the departure ID is not the correct format, return a bad request of the appropriate format" in {
            when(mockAppConfig.enablePhase5).thenReturn(true)
            val request = FakeRequest(
              method = "POST",
              uri = routes.DeparturesRouter.getMessage("01", "0123456789abcdef").url,
              body = <test></test>,
              headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeaderValue))
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
            when(mockAppConfig.enablePhase5).thenReturn(true)
            val request = FakeRequest(
              method = "POST",
              uri = routes.DeparturesRouter.getMessage("0123456789abcdef", "01").url,
              body = <test></test>,
              headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeaderValue))
            )
            val result = sut.getMessage("01234567890bcdef", "01")(request)

            status(result) mustBe BAD_REQUEST
            contentAsJson(result) mustBe Json.obj(
              "code"       -> "BAD_REQUEST",
              "statusCode" -> 400,
              "message"    -> "messageId: Value 01 is not a 16 character hexadecimal string"
            )
          }

          "must route to the v2 controller and return NotAcceptable when phase5 feature is disabled" in {
            when(mockAppConfig.enablePhase5).thenReturn(false)
            val request = FakeRequest(
              method = "POST",
              uri = routes.DeparturesRouter.getMessage("1234567890abcdef", "1234567890abcdef").url,
              body = <test></test>,
              headers = departureHeaders
            )
            val result = call(sut.getMessage("1234567890abcdef", "1234567890abcdef"), request)

            status(result) mustBe NOT_ACCEPTABLE
            contentAsJson(result) mustBe Json.obj(
              "message" -> "CTC Traders API version 2 is not yet available. Please continue to use version 1 to submit transit messages.",
              "code"    -> "NOT_ACCEPTABLE"
            )
          }

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
    Seq(
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON),
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML),
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN)
    ).foreach {
      acceptHeaderValue =>
        val departureHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
        )
        s"with accept header set to $acceptHeaderValue" - {

          "must route to the v2 controller and return Ok when successful" in {
            when(mockAppConfig.enablePhase5).thenReturn(true)
            val request = FakeRequest(method = "GET", body = "", uri = routes.DeparturesRouter.getDeparture("").url, headers = departureHeaders)
            val result  = sut.getDeparture("1234567890abcdef")(request)

            status(result) mustBe OK
            contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
          }

          "must route to the v2 controller and return BAD_REQUEST when departureId has invalid format" in {
            when(mockAppConfig.enablePhase5).thenReturn(true)
            val request = FakeRequest(method = "GET", body = "", uri = routes.DeparturesRouter.getDeparture("").url, headers = departureHeaders)
            val result  = sut.getDeparture("1234567890abcde")(request)

            status(result) mustBe BAD_REQUEST
            contentAsJson(result) mustBe Json.obj(
              "code"       -> "BAD_REQUEST",
              "statusCode" -> 400,
              "message"    -> "departureId: Value 1234567890abcde is not a 16 character hexadecimal string"
            )
          }

          "must route to the v2 controller and return NotAcceptable when phase5 feature is disabled" in {
            when(mockAppConfig.enablePhase5).thenReturn(false)
            val request = FakeRequest(method = "GET", uri = routes.DeparturesRouter.getDeparture("").url, body = "", headers = departureHeaders)
            val result  = call(sut.getDeparture("1234567890abcdef"), request)

            status(result) mustBe NOT_ACCEPTABLE
            contentAsJson(result) mustBe Json.obj(
              "message" -> "CTC Traders API version 2 is not yet available. Please continue to use version 1 to submit transit messages.",
              "code"    -> "NOT_ACCEPTABLE"
            )
          }
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

  "when submitting a new message for an existing departure" - {
    // Version 2
    Seq(
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON),
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML),
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN)
    ).foreach {
      acceptHeaderValue =>
        val departureHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
        )
        s"with accept header set to $acceptHeaderValue" - {

          "must route to the v2 controller and return Accepted when successful" in {
            when(mockAppConfig.enablePhase5).thenReturn(true)
            val request =
              FakeRequest(method = "POST", uri = routes.DeparturesRouter.attachMessage("").url, body = <test></test>, headers = departureHeaders)
            val result = call(sut.attachMessage("1234567890abcdef"), request)

            status(result) mustBe ACCEPTED
            contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
          }

          "must route to the v2 controller and return NotAcceptable when phase5 feature is disabled" in {
            when(mockAppConfig.enablePhase5).thenReturn(false)
            val request = FakeRequest(method = "POST", uri = routes.DeparturesRouter.attachMessage("").url, body = <test></test>, headers = departureHeaders)
            val result  = call(sut.attachMessage("1234567890abcdef"), request)

            status(result) mustBe NOT_ACCEPTABLE
            contentAsJson(result) mustBe Json.obj(
              "message" -> "CTC Traders API version 2 is not yet available. Please continue to use version 1 to submit transit messages.",
              "code"    -> "NOT_ACCEPTABLE"
            )
          }
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
              FakeRequest(method = "POST", uri = routes.DeparturesRouter.attachMessage("123").url, body = <test></test>, headers = departureHeaders)
            val result = call(sut.attachMessage("123"), request)

            status(result) mustBe ACCEPTED
            contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
          }
        }

    }
  }

  "when getting departures for a given enrolment EORI" - {
    // Version 2
    Seq(
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON),
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML),
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN)
    ).foreach {
      acceptHeaderValue =>
        val departureHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
        )
        s"with accept header set to $acceptHeaderValue" - {

          "must route to the v2 controller and return Accepted when successful" in {

            when(mockAppConfig.enablePhase5).thenReturn(true)

            val request =
              FakeRequest(method = "POST", uri = routes.DeparturesRouter.getDeparturesForEori().url, body = <test></test>, headers = departureHeaders)
            val result = call(sut.getDeparturesForEori(), request)

            status(result) mustBe OK
            contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
          }

          "must route to the v2 controller and return NotAcceptable when phase5 feature is disabled" in {

            when(mockAppConfig.enablePhase5).thenReturn(false)

            val request =
              FakeRequest(method = "POST", uri = routes.DeparturesRouter.getDeparturesForEori().url, body = <test></test>, headers = departureHeaders)
            val result = call(sut.getDeparturesForEori(), request)

            status(result) mustBe NOT_ACCEPTABLE
            contentAsJson(result) mustBe Json.obj(
              "message" -> "CTC Traders API version 2 is not yet available. Please continue to use version 1 to submit transit messages.",
              "code"    -> "NOT_ACCEPTABLE"
            )
          }

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
              FakeRequest(method = "POST", uri = routes.DeparturesRouter.getDeparturesForEori().url, body = <test></test>, headers = departureHeaders)
            val result = call(sut.getDeparturesForEori(), request)

            status(result) mustBe OK
            contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
          }
        }

    }
  }

  "when getting departures messages for a given departure" - {
    // Version 2
    Seq(
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON),
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML),
      Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN)
    ).foreach {
      acceptHeaderValue =>
        val departureHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
        )
        s"with accept header set to $acceptHeaderValue" - {

          "must route to the v2 controller and return OK when successful" in {
            when(mockAppConfig.enablePhase5).thenReturn(true)
            val request = FakeRequest(
              method = "POST",
              uri = routes.DeparturesRouter.getMessageIds("1234567890abcdef").url,
              body = <test></test>,
              headers = departureHeaders
            )
            val result = call(sut.getMessageIds("1234567890abcdef", None, None, None, None), request)

            status(result) mustBe OK
            contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action

          }

          "must route to the v2 controller and return NotAcceptable when phase5 feature is disabled" in {
            when(mockAppConfig.enablePhase5).thenReturn(false)
            val request =
              FakeRequest(
                method = "POST",
                uri = routes.DeparturesRouter.getMessageIds("1234567890abcdef").url,
                body = <test></test>,
                headers = departureHeaders
              )
            val result = call(sut.getMessageIds("1234567890abcdef", None, None, None, None), request)

            status(result) mustBe NOT_ACCEPTABLE
            contentAsJson(result) mustBe Json.obj(
              "message" -> "CTC Traders API version 2 is not yet available. Please continue to use version 1 to submit transit messages.",
              "code"    -> "NOT_ACCEPTABLE"
            )
          }

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
              FakeRequest(method = "POST", uri = routes.DeparturesRouter.getDeparturesForEori().url, body = <test></test>, headers = departureHeaders)
            val result = call(sut.getDeparturesForEori(), request)

            status(result) mustBe OK
            contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
          }
        }

    }
  }
}
