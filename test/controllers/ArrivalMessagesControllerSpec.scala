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
import connectors.MessageConnector
import controllers.actions.{AuthAction, FakeAuthAction}
import data.TestXml
import models.domain.MovementMessage
import models.response.ResponseMessage
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, FreeSpec, MustMatchers, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.mvc.AnyContentAsEmpty
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.test.Helpers.{headers, _}
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.Future

class ArrivalMessagesControllerSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach with TestXml{
  private val mockMessageConnector: MessageConnector = mock[MessageConnector]

  override lazy val app = GuiceApplicationBuilder()
    .overrides(bind[AuthAction].to[FakeAuthAction])
    .overrides(bind[MessageConnector].toInstance(mockMessageConnector))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMessageConnector)
  }

  val sourceMovement = MovementMessage(
    "/movements/arrivals/123/messages/4",
    LocalDateTime.of(2020, 2, 2, 2, 2, 2),
    "IE025",
    <test>default</test>)

  val json = Json.toJson[MovementMessage](sourceMovement)

  val expectedResult = Json.toJson[ResponseMessage](ResponseMessage(sourceMovement.location, sourceMovement.dateTime, sourceMovement.messageType, sourceMovement.message))

  def fakeRequestMessages[A](method: String, headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), uri: String, body: A) =
    FakeRequest(method = method, uri = uri, headers, body = body)

  "GET /movements/arrivals/:arrivalId/messages/:messageId" - {
    "return 200 and Message" in {
      when(mockMessageConnector.get(any(), any())(any(), any()))
          .thenReturn(Future.successful(Right(sourceMovement)))

      val request = FakeRequest("GET", "/movements/arrivals/123/messages/4", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      contentAsString(result) mustEqual expectedResult.toString()
      status(result) mustBe OK
    }

    "return 400 if the downstream returns 400" in {
      when(mockMessageConnector.get(any(), any())(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(400))))

      val request = FakeRequest("GET", "/movements/arrivals/123/messages/4", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }

    "return 400 with body if the downstream returns 400 with body" in {
      when(mockMessageConnector.get(any(), any())(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(400, responseString = Some("abc")))))

      val request = FakeRequest("GET", "/movements/arrivals/123/messages/4", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
      contentAsString(result) mustBe "abc"
    }


    "return 404 if the downstream returns 404" in {
      when(mockMessageConnector.get(any(), any())(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(404))))

      val request = FakeRequest("GET", "/movements/arrivals/123/messages/4", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe NOT_FOUND
    }

    "return 500 for other downstream errors" in {
      when(mockMessageConnector.get(any(), any())(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(responseStatus = INTERNAL_SERVER_ERROR, responseJson = Some(json), responseHeaders = Map(), responseString = None) )))

      val request = FakeRequest("GET", "/movements/arrivals/123/messages/4", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "POST /movements/arrivals/:arrivalId/messages" - {
    "must return Accepted when successful" in {
      when(mockMessageConnector.post(any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/1")), responseString = None) ))

      val request = fakeRequestMessages(method = "POST", uri = "/movements/arrivals/123/messages", body = CC044A)
      val result = route(app, request).value

      status(result) mustBe ACCEPTED
      headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123/messages/1")
    }

    "must return InternalServerError when unsuccessful" in {
      when(mockMessageConnector.post(any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR)))

      val request = fakeRequestMessages(method = "POST", uri = "/movements/arrivals/123/messages", body = CC044A)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when no Location in downstream response header" in {
      when(mockMessageConnector.post(any(), any())(any(), any()))
        .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(), responseString = None) ))

      val request = fakeRequestMessages(method = "POST", uri = "/movements/arrivals/123/messages", body = CC044A)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when invalid Location value in downstream response header" in {
      when(mockMessageConnector.post(any(), any())(any(), any()))
        .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/<>")), responseString = None) ))

      val request = fakeRequestMessages(method = "POST", uri = "/movements/arrivals/123/messages", body = CC044A)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must escape arrival ID in Location response header" in {
      when(mockMessageConnector.post(any(), any())(any(), any()))
        .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/123-@+*~-31@")), responseString = None) ))

      val request = fakeRequestMessages(method = "POST", uri = "/movements/arrivals/123/messages", body = CC044A)
      val result = route(app, request).value

      status(result) mustBe ACCEPTED
      headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123/messages/123-%40%2B*%7E-31%40")
    }

    "must exclude query string if present in downstream Location header" in {
      when(mockMessageConnector.post(any(), any())(any(), any()))
        .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/123?status=success")), responseString = None) ))

      val request = fakeRequestMessages(method = "POST", uri = "/movements/arrivals/123/messages", body = CC044A)
      val result = route(app, request).value

      status(result) mustBe ACCEPTED
      headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123/messages/123")
    }

    "must return UnsupportedMediaType when Content-Type is JSON" in {
      val request = FakeRequest(method = "POST", uri = "/movements/arrivals/123/messages", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json")), body = AnyContentAsEmpty)

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when no Content-Type specified" in {
      val request = fakeRequestMessages(method = "POST", headers = FakeHeaders(), uri = "/movements/arrivals/123/messages", body = ByteString("body"))

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when empty XML payload is sent" in {
      val request = fakeRequestMessages(method = "POST", headers = FakeHeaders(), uri = "/movements/arrivals/123/messages", body = AnyContentAsEmpty)

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return BadRequest when invalid XML payload is sent" in {
      val request = fakeRequestMessages(method = "POST", uri = "/movements/arrivals/123/messages", body = InvalidCC044A)

      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }
  }
}
