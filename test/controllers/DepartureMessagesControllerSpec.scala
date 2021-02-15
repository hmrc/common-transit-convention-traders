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

package controllers

import java.time.LocalDateTime

import akka.util.ByteString
import connectors.DepartureMessageConnector
import controllers.actions.{AuthAction, FakeAuthAction}
import data.TestXml
import models.domain.{DepartureWithMessages, MovementMessage}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsNull, Json}
import play.api.mvc.{AnyContentAsEmpty, Headers}
import play.api.test.Helpers.{headers, _}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.HttpResponse
import utils.CallOps._

import scala.concurrent.Future

class DepartureMessagesControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach with TestXml {
  private val mockMessageConnector: DepartureMessageConnector = mock[DepartureMessageConnector]

  override lazy val app = GuiceApplicationBuilder()
    .overrides(bind[AuthAction].to[FakeAuthAction])
    .overrides(bind[DepartureMessageConnector].toInstance(mockMessageConnector))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMessageConnector)
  }

  val sourceMovement = MovementMessage(
    routes.DepartureMessagesController.getDepartureMessage("123","4").urlWithContext,
    LocalDateTime.of(2020, 2, 2, 2, 2, 2),
    "IE025",
    <test>default</test>)

  val sourceDeparture = DepartureWithMessages(123, routes.DeparturesController.getDeparture("123").urlWithContext, routes.DepartureMessagesController.getDepartureMessages("123").urlWithContext, Some("MRN"), "status", LocalDateTime.of(2020, 2, 2, 2, 2, 2), LocalDateTime.of(2020, 2, 2, 2, 2, 2), Seq(sourceMovement, sourceMovement))

  val json = Json.toJson[MovementMessage](sourceMovement)

  val expectedMessageResult = Json.parse(
    """
      |{
      |  "_links": {
      |    "self": {
      |      "href": "/customs/transits/movements/departures/123/messages/4"
      |    },
      |    "departure": {
      |      "href": "/customs/transits/movements/departures/123"
      |    }
      |  },
      |  "departureId": "123",
      |  "messageId": "4",
      |  "received": "2020-02-02T02:02:02",
      |  "messageType": "IE025",
      |  "body": "<test>default</test>"
      |}""".stripMargin)

  val expectedDepartureResult = Json.parse(
    """
      |{
      |  "_links": {
      |    "self": {
      |      "href": "/customs/transits/movements/departures/123/messages"
      |    }
      |  },
      |  "_embedded": {
      |    "messages": [
      |      {
      |        "_links": {
      |          "self": {
      |            "href": "/customs/transits/movements/departures/123/messages/4"
      |          },
      |          "departure": {
      |            "href": "/customs/transits/movements/departures/123"
      |          }
      |        },
      |        "departureId": "123",
      |        "messageId": "4",
      |        "received": "2020-02-02T02:02:02",
      |        "messageType": "IE025",
      |        "body": "<test>default</test>"
      |      },
      |      {
      |        "_links": {
      |          "self": {
      |            "href": "/customs/transits/movements/departures/123/messages/4"
      |          },
      |          "departure": {
      |            "href": "/customs/transits/movements/departures/123"
      |          }
      |        },
      |        "departureId": "123",
      |        "messageId": "4",
      |        "received": "2020-02-02T02:02:02",
      |        "messageType": "IE025",
      |        "body": "<test>default</test>"
      |      }
      |    ],
      |    "departure": {
      |      "id": "123",
      |      "created": "2020-02-02T02:02:02",
      |      "updated": "2020-02-02T02:02:02",
      |      "movementReferenceNumber": "MRN",
      |      "status": "status",
      |      "_links": {
      |        "self": {
      |          "href": "/customs/transits/movements/departures/123"
      |        },
      |        "messages": {
      |          "href": "/customs/transits/movements/departures/123/messages"
      |        }
      |      }
      |    }
      |  }
      |}""".stripMargin)

  def fakeRequestMessages[A](method: String, headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), uri: String, body: A) =
    FakeRequest(method = method, uri = uri, headers, body = body)

  "GET /movements/departures/:departureId/messages" - {
    "return 200 with body of departure and messages" in {
      when(mockMessageConnector.getMessages(any())(any(), any(), any()))
        .thenReturn(Future.successful(Right(sourceDeparture)))

      val request = FakeRequest("GET", routes.DepartureMessagesController.getDepartureMessages("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedDepartureResult.toString()
    }

    "return 404 if downstream returns 404" in {
      when(mockMessageConnector.getMessages(any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(404, ""))))

      val request = FakeRequest("GET", routes.DepartureMessagesController.getDepartureMessages("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe NOT_FOUND
    }

    "return 500 for other downstream errors" in {
      when(mockMessageConnector.getMessages(any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(INTERNAL_SERVER_ERROR, json, Headers.create().toMap) )))

      val request = FakeRequest("GET", routes.DepartureMessagesController.getDepartureMessages("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "return 500 if downstream provides an unsafe message header" ignore {
      when(mockMessageConnector.getMessages(any())(any(), any(), any()))
        .thenReturn(Future.successful(Right(sourceDeparture.copy(messages = Seq(sourceMovement.copy(location = "/transits-movements-trader-at-departure/movements/departures/<>"))))))

      val request = FakeRequest("GET", routes.DepartureMessagesController.getDepartureMessages("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "GET /movements/departures/:departureId/messages/:messageId" - {
    "return 200 and Message" in {
      when(mockMessageConnector.get(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Right(sourceMovement)))

      val request = FakeRequest("GET", routes.DepartureMessagesController.getDepartureMessage("123","4").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedMessageResult.toString()
    }

    "return 400 if the downstream returns 400" in {
      when(mockMessageConnector.get(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(400, ""))))

      val request = FakeRequest("GET", routes.DepartureMessagesController.getDepartureMessage("123","4").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }

    "return 400 with body if the downstream returns 400 with body" in {
      when(mockMessageConnector.get(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(400, "abc"))))

      val request = FakeRequest("GET", routes.DepartureMessagesController.getDepartureMessage("123","4").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
      contentAsString(result) mustBe "abc"
    }

    "return 404 if the downstream returns 404" in {
      when(mockMessageConnector.get(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(404, ""))))

      val request = FakeRequest("GET", routes.DepartureMessagesController.getDepartureMessage("123","4").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe NOT_FOUND
    }

    "return 500 for other downstream errors" in {
      when(mockMessageConnector.get(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse.apply(INTERNAL_SERVER_ERROR, json, Headers.create().toMap) )))

      val request = FakeRequest("GET", routes.DepartureMessagesController.getDepartureMessage("123","4").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "POST /movements/departures/:departureId/messages" - {
    val expectedJson = Json.parse(
      """
        |{
        |  "_links": {
        |    "self": {
        |      "href": "/customs/transits/movements/departures/123/messages/1"
        |    },
        |    "departure": {
        |      "href": "/customs/transits/movements/departures/123"
        |    }
        |  },
        |  "departureId": "123",
        |  "messageId": "1",
        |  "messageType": "IE014",
        |  "body": "<CC014A>\n    <SynIdeMES1>tval</SynIdeMES1>\n    <SynVerNumMES2>1</SynVerNumMES2>\n    \n    <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>\n    <MesRecMES6>111111</MesRecMES6>\n    \n    <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>\n    <DatOfPreMES9>20001001</DatOfPreMES9>\n    <TimOfPreMES10>1111</TimOfPreMES10>\n    <IntConRefMES11>111111</IntConRefMES11>\n    \n    <RecRefMES12>111111</RecRefMES12>\n    \n    <RecRefQuaMES13>to</RecRefQuaMES13>\n    \n    <AppRefMES14>token</AppRefMES14>\n    \n    <PriMES15>t</PriMES15>\n    \n    <AckReqMES16>1</AckReqMES16>\n    \n    <ComAgrIdMES17>token</ComAgrIdMES17>\n    \n    <TesIndMES18>1</TesIndMES18>\n    <MesIdeMES19>token</MesIdeMES19>\n    <MesTypMES20>token</MesTypMES20>\n    \n    <ComAccRefMES21>token</ComAccRefMES21>\n    \n    <MesSeqNumMES22>11</MesSeqNumMES22>\n    \n    <FirAndLasTraMES23>t</FirAndLasTraMES23>\n    <HEAHEA>\n      <DocNumHEA5>default</DocNumHEA5>\n      <DatOfCanReqHEA147>20001001</DatOfCanReqHEA147>\n      <CanReaHEA250>default</CanReaHEA250>\n      <CanReaHEA250LNG>ab</CanReaHEA250LNG>\n    </HEAHEA>\n    <TRAPRIPC1>\n    </TRAPRIPC1>\n    <CUSOFFDEPEPT>\n      <RefNumEPT1>default1</RefNumEPT1>\n    </CUSOFFDEPEPT>\n  </CC014A>"
        |}""".stripMargin)

    "must return Accepted when successful" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123/messages/1"))) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, body = CC014A)
      val result = route(app, request).value

      status(result) mustBe ACCEPTED
      contentAsString(result) mustEqual expectedJson.toString()
      headers(result) must contain (LOCATION -> routes.DepartureMessagesController.getDepartureMessage("123", "1").urlWithContext)
    }

    "must return InternalServerError when unsuccessful" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))

      val request = fakeRequestMessages(method = "POST", uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, body = CC014A)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when no Location in downstream response header" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Headers.create().toMap) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, body = CC014A)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when invalid Location value in downstream response header" ignore {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123/messages/<>"))) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, body = CC014A)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must escape departure ID in Location response header" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123/messages/123-@+*~-31@"))) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, body = CC014A)
      val result = route(app, request).value

      val expectedJson = Json.parse(
        """
          |{
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/departures/123/messages/123-@+*~-31@"
          |    },
          |    "departure": {
          |      "href": "/customs/transits/movements/departures/123"
          |    }
          |  },
          |  "departureId": "123",
          |  "messageId": "123-@+*~-31@",
          |  "messageType": "IE014",
          |  "body": "<CC014A>\n    <SynIdeMES1>tval</SynIdeMES1>\n    <SynVerNumMES2>1</SynVerNumMES2>\n    \n    <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>\n    <MesRecMES6>111111</MesRecMES6>\n    \n    <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>\n    <DatOfPreMES9>20001001</DatOfPreMES9>\n    <TimOfPreMES10>1111</TimOfPreMES10>\n    <IntConRefMES11>111111</IntConRefMES11>\n    \n    <RecRefMES12>111111</RecRefMES12>\n    \n    <RecRefQuaMES13>to</RecRefQuaMES13>\n    \n    <AppRefMES14>token</AppRefMES14>\n    \n    <PriMES15>t</PriMES15>\n    \n    <AckReqMES16>1</AckReqMES16>\n    \n    <ComAgrIdMES17>token</ComAgrIdMES17>\n    \n    <TesIndMES18>1</TesIndMES18>\n    <MesIdeMES19>token</MesIdeMES19>\n    <MesTypMES20>token</MesTypMES20>\n    \n    <ComAccRefMES21>token</ComAccRefMES21>\n    \n    <MesSeqNumMES22>11</MesSeqNumMES22>\n    \n    <FirAndLasTraMES23>t</FirAndLasTraMES23>\n    <HEAHEA>\n      <DocNumHEA5>default</DocNumHEA5>\n      <DatOfCanReqHEA147>20001001</DatOfCanReqHEA147>\n      <CanReaHEA250>default</CanReaHEA250>\n      <CanReaHEA250LNG>ab</CanReaHEA250LNG>\n    </HEAHEA>\n    <TRAPRIPC1>\n    </TRAPRIPC1>\n    <CUSOFFDEPEPT>\n      <RefNumEPT1>default1</RefNumEPT1>\n    </CUSOFFDEPEPT>\n  </CC014A>"
          |}""".stripMargin)

      status(result) mustBe ACCEPTED
      contentAsString(result) mustEqual expectedJson.toString()
      headers(result) must contain (LOCATION -> routes.DepartureMessagesController.getDepartureMessage("123", "123-@+*~-31@").urlWithContext)
    }

    "must exclude query string if present in downstream Location header" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123/messages/1?status=success"))) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, body = CC014A)
      val result = route(app, request).value

      status(result) mustBe ACCEPTED
      contentAsString(result) mustEqual expectedJson.toString()
      headers(result) must contain (LOCATION -> routes.DepartureMessagesController.getDepartureMessage("123","1").urlWithContext)
    }

    "must return UnsupportedMediaType when Content-Type is JSON" in {
      val request = FakeRequest(method = "POST", uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json")), body = AnyContentAsEmpty)

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when no Content-Type specified" in {
      val request = fakeRequestMessages(method = "POST", headers = FakeHeaders(), uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, body = ByteString("body"))

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when empty XML payload is sent" in {
      val request = fakeRequestMessages(method = "POST", headers = FakeHeaders(), uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, body = AnyContentAsEmpty)

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return BadRequest when invalid XML payload is sent" in {
      val request = fakeRequestMessages(method = "POST", uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, body = InvalidCC014A)

      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }
  }

}
