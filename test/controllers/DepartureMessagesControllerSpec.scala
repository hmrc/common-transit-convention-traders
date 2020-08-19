/*
 * Copyright 2020 HM Revenue & Customs
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
import models.response.{ResponseDepartureWithMessages, ResponseMessage}
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

  val sourceDeparture = DepartureWithMessages(123, routes.DeparturesController.getDeparture("123").urlWithContext, routes.DepartureMessagesController.getDepartureMessages("123").urlWithContext, Some("MRN"), "ref", "status", LocalDateTime.of(2020, 2, 2, 2, 2, 2), LocalDateTime.of(2020, 2, 2, 2, 2, 2), Seq(sourceMovement, sourceMovement))

  val json = Json.toJson[MovementMessage](sourceMovement)

  val expectedMessage = ResponseMessage(sourceMovement.location, sourceMovement.dateTime, sourceMovement.messageType, sourceMovement.message)
  val expectedMessageResult = Json.toJson[ResponseMessage](expectedMessage)
  val expectedDeparture = ResponseDepartureWithMessages(sourceDeparture.location, sourceDeparture.created, sourceDeparture.updated, sourceDeparture.movementReferenceNumber, sourceDeparture.referenceNumber, sourceDeparture.status, Seq(expectedMessage, expectedMessage))
  val expectedDepartureResult = Json.toJson[ResponseDepartureWithMessages](expectedDeparture)

  def fakeRequestMessages[A](method: String, headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), uri: String, body: A) =
    FakeRequest(method = method, uri = uri, headers, body = body)

  "GET /movements/departures/:departureId/messages" - {
    "return 200 with body of departure and messages" in {
        when(mockMessageConnector.getMessages(any())(any(), any(), any()))
          .thenReturn(Future.successful(Right(sourceDeparture)))

        val request = FakeRequest("GET", routes.DepartureMessagesController.getDepartureMessages("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
        val result = route(app, request).value

        contentAsString(result) mustEqual expectedDepartureResult.toString()
        status(result) mustBe OK
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

      contentAsString(result) mustEqual expectedMessageResult.toString()
      status(result) mustBe OK
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
    "must return Accepted when successful" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123/messages/1"))) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, body = CC014A)
      val result = route(app, request).value

      status(result) mustBe ACCEPTED
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

      status(result) mustBe ACCEPTED
      headers(result) must contain (LOCATION -> routes.DepartureMessagesController.getDepartureMessage("123", "123-@+*~-31@").urlWithContext)
    }

    "must exclude query string if present in downstream Location header" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123/messages/123?status=success"))) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.DepartureMessagesController.sendMessageDownstream("123").url, body = CC014A)
      val result = route(app, request).value

      status(result) mustBe ACCEPTED
      headers(result) must contain (LOCATION -> routes.DepartureMessagesController.getDepartureMessage("123","123").urlWithContext)
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
