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

package controllers

import config.Constants.MissingECCEnrolmentMessage
import config.Constants.XMissingECCEnrolment
import connectors.ArrivalMessageConnector
import controllers.actions.AuthAction
import controllers.actions.FakeAuthAction
import controllers.actions.FakeAuthEccEnrollmentHeaderAction
import data.TestXml
import models.domain.ArrivalId
import models.domain.ArrivalWithMessages
import models.domain.MessageId
import models.domain.MovementMessage
import models.response.JsonClientErrorResponse
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsNull
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Headers
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HttpResponse
import v2.utils.CallOps._

import java.time.Clock
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.Future

class ArrivalMessagesControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterEach
    with TestXml {
  private val mockMessageConnector: ArrivalMessageConnector = mock[ArrivalMessageConnector]

  val mockClock = mock[Clock]

  override lazy val app = GuiceApplicationBuilder()
    .overrides(
      bind[AuthAction].to[FakeAuthAction],
      bind[ArrivalMessageConnector].toInstance(mockMessageConnector),
      bind[Clock].toInstance(mockClock)
    )
    .configure(
      "phase-4-enrolment-header" -> false,
      "metrics.jvm"              -> false
    )
    .build()

  val appWithEnrollmentHeader = GuiceApplicationBuilder()
    .overrides(
      bind[AuthAction].to[FakeAuthEccEnrollmentHeaderAction],
      bind[ArrivalMessageConnector].toInstance(mockMessageConnector),
      bind[Clock].toInstance(mockClock)
    )
    .configure(
      "phase-4-enrolment-header" -> true,
      "metrics.jvm"              -> false
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMessageConnector)
  }

  val sourceMovement = MovementMessage(
    routing.routes.ArrivalsRouter.getArrivalMessage("123", "4").urlWithContext,
    LocalDateTime.of(2020, 2, 2, 2, 2, 2),
    "IE025",
    <test>default</test>,
    Some(LocalDateTime.of(2020, 2, 2, 2, 2, 2))
  )

  val sourceArrival = ArrivalWithMessages(
    ArrivalId(123),
    routing.routes.ArrivalsRouter.getArrival("123").urlWithContext,
    routing.routes.ArrivalsRouter.getArrivalMessageIds("123").urlWithContext,
    "MRN",
    LocalDateTime.of(2020, 2, 2, 2, 2, 2),
    LocalDateTime.of(2020, 2, 2, 2, 2, 2),
    Seq(sourceMovement, sourceMovement)
  )

  val json = Json.toJson[MovementMessage](sourceMovement)

  val expectedMessageResult = Json.parse("""
      |{
      |  "_links": {
      |    "self": {
      |      "href": "/customs/transits/movements/arrivals/123/messages/4"
      |    },
      |    "arrival": {
      |      "href": "/customs/transits/movements/arrivals/123"
      |    }
      |  },
      |  "arrivalId": "123",
      |  "messageId": "4",
      |  "received": "2020-02-02T02:02:02",
      |  "messageType": "IE025",
      |  "body": "<test>default</test>"
      |}
      |""".stripMargin)

  val expectedArrivalResult = Json.parse("""
      |{
      |  "_links": {
      |    "self": {
      |      "href": "/customs/transits/movements/arrivals/123/messages"
      |    }
      |  },
      |  "_embedded": {
      |    "messages": [
      |      {
      |        "_links": {
      |          "self": {
      |            "href": "/customs/transits/movements/arrivals/123/messages/4"
      |          },
      |          "arrival": {
      |            "href": "/customs/transits/movements/arrivals/123"
      |          }
      |        },
      |        "arrivalId": "123",
      |        "messageId": "4",
      |        "received": "2020-02-02T02:02:02",
      |        "messageType": "IE025",
      |        "body": "<test>default</test>"
      |      },
      |      {
      |        "_links": {
      |          "self": {
      |            "href": "/customs/transits/movements/arrivals/123/messages/4"
      |          },
      |          "arrival": {
      |            "href": "/customs/transits/movements/arrivals/123"
      |          }
      |        },
      |        "arrivalId": "123",
      |        "messageId": "4",
      |        "received": "2020-02-02T02:02:02",
      |        "messageType": "IE025",
      |        "body": "<test>default</test>"
      |      }
      |    ],
      |    "arrival": {
      |      "id": "123",
      |      "created": "2020-02-02T02:02:02",
      |      "updated": "2020-02-02T02:02:02",
      |      "movementReferenceNumber": "MRN",
      |      "_links": {
      |        "self": {
      |          "href": "/customs/transits/movements/arrivals/123"
      |        },
      |        "messages": {
      |          "href": "/customs/transits/movements/arrivals/123/messages"
      |        }
      |      }
      |    }
      |  }
      |}""".stripMargin)

  def fakeRequestMessages[A](method: String, headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), uri: String, body: A) =
    FakeRequest(method = method, uri = uri, headers, body = body)

  "GET /movements/arrivals/:arrivalId/messages/:messageId" - {
    "return 200 and Message" in {
      when(mockMessageConnector.get(ArrivalId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(Right(sourceMovement)))

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessage("123", "4").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedMessageResult.toString()
    }

    "return 200 and Message which has XMissingECCEnrolment header in response" in {
      when(mockMessageConnector.get(ArrivalId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(Right(sourceMovement)))

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessage("123", "4").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(appWithEnrollmentHeader, request).value

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedMessageResult.toString()
      headers(result) must contain(XMissingECCEnrolment -> MissingECCEnrolmentMessage)
    }

    "return 400 if the downstream returns 400" in {
      when(mockMessageConnector.get(ArrivalId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(400, ""))))

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessage("123", "4").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }

    "return 400 with body if the downstream returns 400 with body" in {
      val testStatusCode = 400
      val testMessage    = "abc"

      when(mockMessageConnector.get(ArrivalId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(testStatusCode, testMessage))))

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessage("123", "4").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      val expectedResult = Json.obj(
        "code"       -> JsonClientErrorResponse.errorCode,
        "message"    -> testMessage,
        "statusCode" -> testStatusCode
      )

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe expectedResult
    }

    "return 404 if the downstream returns 404" in {
      when(mockMessageConnector.get(ArrivalId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(404, ""))))

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessage("123", "4").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe NOT_FOUND
    }

    "return 500 for other downstream errors" in {
      when(mockMessageConnector.get(ArrivalId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse.apply(INTERNAL_SERVER_ERROR, json, Headers.create().toMap))))

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessage("123", "4").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "POST /movements/arrivals/:arrivalId/messages" - {
    "must return Accepted when successful" in {
      when(mockMessageConnector.post(any(), ArrivalId(any()))(any(), any()))
        .thenReturn(
          Future.successful(
            HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/1")))
          )
        )

      val request = fakeRequestMessages(method = "POST", uri = routing.routes.ArrivalsRouter.attachMessage("123").url, body = CC044A)
      val result  = route(app, request).value

      val expectedJson = Json.parse(s"""
           |{
           |  "_links": {
           |    "self": {
           |      "href": "/customs/transits/movements/arrivals/123/messages/1"
           |    },
           |    "arrival": {
           |      "href": "/customs/transits/movements/arrivals/123"
           |    }
           |  },
           |  "arrivalId": "123",
           |  "messageId": "1",
           |  "messageType": "IE044",
           |  "body": ${JsString(CC044A.toString)},
           |  "_embedded": {
           |    "notifications": {
           |      "requestId": "/customs/transits/movements/arrivals/123"
           |    }
           |  }
           |}""".stripMargin)

      status(result) mustBe ACCEPTED
      contentAsString(result) mustEqual expectedJson.toString()
      headers(result) must contain(LOCATION -> routing.routes.ArrivalsRouter.getArrivalMessage("123", "1").urlWithContext)
    }

    "must return Accepted when successful has XMissingECCEnrolment header in response" in {
      when(mockMessageConnector.post(any(), ArrivalId(any()))(any(), any()))
        .thenReturn(
          Future.successful(
            HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/1")))
          )
        )

      val request = fakeRequestMessages(method = "POST", uri = routing.routes.ArrivalsRouter.attachMessage("123").url, body = CC044A)
      val result  = route(appWithEnrollmentHeader, request).value

      val expectedJson = Json.parse(s"""
          |{
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/arrivals/123/messages/1"
          |    },
          |    "arrival": {
          |      "href": "/customs/transits/movements/arrivals/123"
          |    }
          |  },
          |  "arrivalId": "123",
          |  "messageId": "1",
          |  "messageType": "IE044",
          |  "body": ${JsString(CC044A.toString)},
          |  "_embedded":{
          |    "notifications": {
          |      "requestId": "/customs/transits/movements/arrivals/123"
          |    }
          |  }
          |}""".stripMargin)

      status(result) mustBe ACCEPTED
      contentAsString(result) mustEqual expectedJson.toString()
      headers(result) must contain(LOCATION -> routing.routes.ArrivalsRouter.getArrivalMessage("123", "1").urlWithContext)
      headers(result) must contain(XMissingECCEnrolment -> MissingECCEnrolmentMessage)
    }

    "must return BadRequest when xml includes MesSenMES3" in {
      val request =
        fakeRequestMessages(method = "POST", uri = routing.routes.ArrivalsRouter.attachMessage("123").url, body = CC044AwithMesSenMES3)
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }

    "must return InternalServerError when unsuccessful" in {
      when(mockMessageConnector.post(any(), ArrivalId(any()))(any(), any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))

      val request = fakeRequestMessages(method = "POST", uri = routing.routes.ArrivalsRouter.attachMessage("123").url, body = CC044A)
      val result  = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when no Location in downstream response header" in {
      when(mockMessageConnector.post(any(), ArrivalId(any()))(any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Headers.create().toMap)))

      val request = fakeRequestMessages(method = "POST", uri = routing.routes.ArrivalsRouter.attachMessage("123").url, body = CC044A)
      val result  = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when invalid Location value in downstream response header" ignore {
      when(mockMessageConnector.post(any(), ArrivalId(any()))(any(), any()))
        .thenReturn(
          Future.successful(
            HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/<>")))
          )
        )

      val request = fakeRequestMessages(method = "POST", uri = routing.routes.ArrivalsRouter.attachMessage("123").url, body = CC044A)
      val result  = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must exclude query string if present in downstream Location headerYYY" in {
      when(mockMessageConnector.post(any(), ArrivalId(any()))(any(), any()))
        .thenReturn(
          Future.successful(
            HttpResponse(
              NO_CONTENT,
              JsNull,
              Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/123?status=success"))
            )
          )
        )

      val request = fakeRequestMessages(method = "POST", uri = routing.routes.ArrivalsRouter.attachMessage("123").url, body = CC044A)
      val result  = route(app, request).value

      val expectedJson = Json.parse(s"""
          |{
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/arrivals/123/messages/123"
          |    },
          |    "arrival": {
          |      "href": "/customs/transits/movements/arrivals/123"
          |    }
          |  },
          |  "arrivalId": "123",
          |  "messageId": "123",
          |  "messageType": "IE044",
          |  "body": ${JsString(CC044A.toString)},
          |  "_embedded": {
          |    "notifications": {
          |      "requestId": "/customs/transits/movements/arrivals/123"
          |    }
          |  }
          |}""".stripMargin)

      status(result) mustBe ACCEPTED
      contentAsString(result) mustEqual expectedJson.toString()
      headers(result) must contain(LOCATION -> routing.routes.ArrivalsRouter.getArrivalMessage("123", "123").urlWithContext)
    }

    "must return UnsupportedMediaType when Content-Type is JSON" in {
      val request = FakeRequest(
        method = "POST",
        uri = routing.routes.ArrivalsRouter.attachMessage("123").url,
        headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json")),
        body = AnyContentAsEmpty
      )

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when no Content-Type specified" in {
      val request = fakeRequestMessages(
        method = "POST",
        headers = FakeHeaders(),
        uri = routing.routes.ArrivalsRouter.attachMessage("123").url,
        body = ByteString("body")
      )

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when empty XML payload is sent" in {
      val request = fakeRequestMessages(
        method = "POST",
        headers = FakeHeaders(),
        uri = routing.routes.ArrivalsRouter.attachMessage("123").url,
        body = AnyContentAsEmpty
      )

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return BadRequest when invalid XML payload is sent" in {
      val request = fakeRequestMessages(method = "POST", uri = routing.routes.ArrivalsRouter.attachMessage("123").url, body = InvalidCC044A)

      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }
  }

  "GET /movements/arrivals/:arrivalId/messages" - {
    "return 200 with body of arrival and messages" in {
      when(mockMessageConnector.getMessages(ArrivalId(any()), any())(any(), any()))
        .thenReturn(Future.successful(Right(sourceArrival)))

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessageIds("123").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )

      val result = route(app, request).value

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedArrivalResult.toString()
    }

    "return 200 with body of arrival and messages has XMissingECCEnrolment header in response" in {
      when(mockMessageConnector.getMessages(ArrivalId(any()), any())(any(), any()))
        .thenReturn(Future.successful(Right(sourceArrival)))

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessageIds("123").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )

      val result = route(appWithEnrollmentHeader, request).value

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedArrivalResult.toString()
      headers(result) must contain(XMissingECCEnrolment -> MissingECCEnrolmentMessage)
    }

    "pass receivedSince parameter on to connector" in {
      val argCaptor = ArgumentCaptor.forClass(classOf[Option[OffsetDateTime]])
      val dateTime  = Some(OffsetDateTime.of(2021, 6, 23, 12, 1, 24, 0, ZoneOffset.UTC))

      when(mockMessageConnector.getMessages(ArrivalId(any()), argCaptor.capture())(any(), any()))
        .thenReturn(Future.successful(Right(sourceArrival)))

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessageIds("123", dateTime).url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe OK
      argCaptor.getValue() mustBe dateTime
    }

    "return 404 if downstream returns 404" in {
      when(mockMessageConnector.getMessages(ArrivalId(any()), any())(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(404, ""))))

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessageIds("123").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe NOT_FOUND
    }

    "return 500 for other downstream errors" in {
      when(mockMessageConnector.getMessages(ArrivalId(any()), any())(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(INTERNAL_SERVER_ERROR, json, Headers.create().toMap))))

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessageIds("123").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "return 500 if downstream provides an unsafe message header" ignore {
      when(mockMessageConnector.getMessages(ArrivalId(any()), any())(any(), any()))
        .thenReturn(
          Future.successful(
            Right(sourceArrival.copy(messages = Seq(sourceMovement.copy(location = "/transit-movements-trader-at-destination/movements/arrivals/<>"))))
          )
        )

      val request = FakeRequest(
        "GET",
        routing.routes.ArrivalsRouter.getArrivalMessageIds("123").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }
}
