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

import java.time.{LocalDate, LocalTime}

import controllers.actions.{AuthAction, FakeAuthAction}
import akka.util.ByteString
import connectors.{ArrivalConnector, MessageConnector}
import data.TestXml
import models.domain.MovementMessage
import models.response.Message
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, FreeSpec, MustMatchers, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.test.Helpers.{headers, _}

import scala.xml.NodeSeq
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HttpResponse, Upstream4xxResponse, Upstream5xxResponse}

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
    LocalDate.of(2020, 2, 2),
    LocalTime.of(2,2,2).toString,
    "IE025",
    "<test></test>")

  val json = Json.toJson[MovementMessage](sourceMovement)

  val expectedResult = Json.toJson[Message](Message(sourceMovement.location, sourceMovement.date, sourceMovement.message))

  "GET /movements/arrivals/:arrivalId/messages/:messageId" - {
    "return 200 and Message" in {
      when(mockMessageConnector.get(any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(responseStatus = OK, responseJson = Some(json), responseHeaders = Map(LOCATION -> Seq("/arrivals/123")), responseString = None) ))

      val request = FakeRequest("GET", "/movements/arrivals/123/messages/4", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      contentAsString(result) mustEqual expectedResult.toString()
      status(result) mustBe OK
    }

    "return 500 if the downstream location header is missing" in {
      when(mockMessageConnector.get(any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(responseStatus = OK, responseJson = Some(json), responseHeaders = Map(), responseString = None) ))

      val request = FakeRequest("GET", "/movements/arrivals/123/messages/4", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR

    }

    "return 400 if the downstream returns 400" in {
      when(mockMessageConnector.get(any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(400)))

      val request = FakeRequest("GET", "/movements/arrivals/123/messages/4", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }

    "return 404 if the downstream returns 404" in {
      when(mockMessageConnector.get(any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(404)))

      val request = FakeRequest("GET", "/movements/arrivals/123/messages/4", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe NOT_FOUND

    }

    "return 500 for other downstream errors" in {
      when(mockMessageConnector.get(any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(responseStatus = INTERNAL_SERVER_ERROR, responseJson = Some(json), responseHeaders = Map(), responseString = None) ))

      val request = FakeRequest("GET", "/movements/arrivals/123/messages/4", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR

    }
  }
}
