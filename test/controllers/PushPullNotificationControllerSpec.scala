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
import java.time.OffsetDateTime
import java.time.ZoneOffset

import akka.util.ByteString
import com.kenshoo.play.metrics.Metrics
import config.Constants
import connectors.{DepartureMessageConnector, PushPullNotificationConnector}
import controllers.actions.AuthAction
import controllers.actions.FakeAuthAction
import data.TestXml
import models.{Box, BoxId}
import models.domain.DepartureId
import models.domain.DepartureWithMessages
import models.domain.MessageId
import models.domain.MovementMessage
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsNull
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Headers
import play.api.test.Helpers.headers
import play.api.test.Helpers._
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.CallOps._
import utils.TestMetrics

import scala.concurrent.Future
import models.response.{HateoasResponseBox, JsonClientErrorResponse}

class PushPullNotificationControllerSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterEach
    with TestXml {

  val mockPushPullNotificationConnector = mock[PushPullNotificationConnector]

  override lazy val app = GuiceApplicationBuilder()
    .overrides(
      bind[Metrics].toInstance(new TestMetrics),
      bind[AuthAction].to[FakeAuthAction],
      bind[PushPullNotificationConnector].toInstance(mockPushPullNotificationConnector)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPushPullNotificationConnector)
  }

  def fakeRequestMessages[A](method: String, headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), uri: String, body: A) =
    FakeRequest(method = method, uri = uri, headers, body = body)

  "GET /movements/push-pull-notifications/box" - {

    "returns BoxInfo if box exists for clientId" in {
      val box = Box(BoxId("testId"), "boxName")
      when(mockPushPullNotificationConnector.getBox(any())(any(), any()))
        .thenReturn(Future.successful(Right(box)))

      val request = FakeRequest("GET",
        routes.PushPullNotificationController.getBoxInfo().url,
        headers = FakeHeaders(Seq(Constants.XClientIdHeader -> "foo", HeaderNames.ACCEPT -> "application/vnd.hrmc.1.0+json")),
        AnyContentAsEmpty)

      val result = route(app, request).value

      status(result) mustBe OK
      contentAsJson(result) mustEqual Json.toJson(HateoasResponseBox(box))

    }

    "returns NotFound if no box exists for clientId" in {
      when(mockPushPullNotificationConnector.getBox(any())(any(), any()))
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("", NOT_FOUND))))

      val request = FakeRequest("GET",
        routes.PushPullNotificationController.getBoxInfo().url,
        headers = FakeHeaders(Seq(Constants.XClientIdHeader -> "foo", HeaderNames.ACCEPT -> "application/vnd.hrmc.1.0+json")),
        AnyContentAsEmpty)

      val result = route(app, request).value

      status(result) mustBe NOT_FOUND
    }

    "returns InternalServerError if unexpected error received from PushPullNotificationService" in {
      when(mockPushPullNotificationConnector.getBox(any())(any(), any()))
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR))))

      val request = FakeRequest("GET",
        routes.PushPullNotificationController.getBoxInfo().url,
        headers = FakeHeaders(Seq(Constants.XClientIdHeader -> "foo", HeaderNames.ACCEPT -> "application/vnd.hrmc.1.0+json")),
        AnyContentAsEmpty)

      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "returns BadRequest if ClientId not found" in {
      val request = FakeRequest("GET",
        routes.PushPullNotificationController.getBoxInfo().url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hrmc.1.0+json")),
        AnyContentAsEmpty)

      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }

  }

}
