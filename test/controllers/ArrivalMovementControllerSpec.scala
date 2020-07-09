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

import controllers.actions.{AuthAction, FakeAuthAction}
import akka.util.ByteString
import connectors.ArrivalConnector
import data.TestXml
import models.domain.{Arrival, Arrivals}
import models.response.{ResponseArrival, ResponseArrivals}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.mvc.{AnyContentAsEmpty, Headers}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.test.Helpers.{headers, _}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.http.HttpResponse
import utils.CallOps._

import scala.concurrent.Future

class ArrivalMovementControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach with TestXml {
  private val mockArrivalConnector: ArrivalConnector = mock[ArrivalConnector]

  override lazy val app = GuiceApplicationBuilder()
    .overrides(bind[AuthAction].to[FakeAuthAction])
    .overrides(bind[ArrivalConnector].toInstance(mockArrivalConnector))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockArrivalConnector)
  }

  val sourceArrival = Arrival(123, routes.ArrivalMovementController.getArrival("123").urlWithContext, routes.ArrivalMessagesController.getArrivalMessages("123").urlWithContext, "MRN", "status", LocalDateTime.of(2020, 2, 2, 2, 2, 2), LocalDateTime.of(2020, 2, 2, 2, 2, 2))
  val expectedArrival = ResponseArrival(sourceArrival)
  val expectedArrivalResult = Json.toJson[ResponseArrival](expectedArrival)

  def fakeRequestArrivals[A](method: String, headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), uri: String = routes.ArrivalMovementController.createArrivalNotification().url, body: A) =
    FakeRequest(method = method, uri = uri, headers, body = body)

 "POST /movements/arrivals" - {
   "must return Accepted when successful" in {
     when(mockArrivalConnector.post(any())(any(), any(), any()))
       .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123"))) ))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> routes.ArrivalMovementController.getArrival("123").urlWithContext)
   }

   "must return InternalServerError when unsuccessful" in {
     when(mockArrivalConnector.post(any())(any(), any(), any()))
       .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when no Location in downstream response header" in {
     when(mockArrivalConnector.post(any())(any(), any(), any()))
       .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Headers.create().toMap) ))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when invalid Location value in downstream response header" ignore {
     when(mockArrivalConnector.post(any())(any(), any(), any()))
       .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/<>"))) ))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must escape arrival ID in Location response header" in {
     when(mockArrivalConnector.post(any())(any(), any(), any()))
       .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123-@+*~-31@"))) ))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> routes.ArrivalMovementController.getArrival("123-@+*~-31@").urlWithContext)
   }

   "must exclude query string if present in downstream Location header" in {
     when(mockArrivalConnector.post(any())(any(), any(), any()))
       .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123?status=success"))) ))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> routes.ArrivalMovementController.getArrival("123").urlWithContext)
   }

   "must return UnsupportedMediaType when Content-Type is JSON" in {
     val request = FakeRequest(method = "POST", uri = "/movements/arrivals", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json")), body = AnyContentAsEmpty)

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return UnsupportedMediaType when no Content-Type specified" in {
     val request = fakeRequestArrivals(method = "POST", headers = FakeHeaders(), body = ByteString("body"))

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return UnsupportedMediaType when empty XML payload is sent" in {
     val request = fakeRequestArrivals(method = "POST", headers = FakeHeaders(), body = AnyContentAsEmpty)

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return BadRequest when invalid XML payload is sent" in {
     val request = fakeRequestArrivals(method = "POST", body = InvalidCC007A)

     val result = route(app, request).value

     status(result) mustBe BAD_REQUEST
   }
 }

 "PUT /movements/arrivals/:arrivalId" - {

   val request = fakeRequestArrivals(method = "PUT", uri = routes.ArrivalMovementController.resubmitArrivalNotification("123").url, body = CC007A)

   "must return Accepted when successful" in {
     when(mockArrivalConnector.put(any(), any())(any(), any(), any()))
       .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123"))) ))

     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> routes.ArrivalMovementController.getArrival("123").urlWithContext)
   }

   "must return InternalServerError when unsuccessful" in {
     when(mockArrivalConnector.put(any(), any())(any(), any(), any()))
       .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))

     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when no Location in downstream response header" in {
     when(mockArrivalConnector.put(any(), any())(any(), any(), any()))
       .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Headers.create().toMap) ))

     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when invalid Location value in downstream response header" ignore {
     when(mockArrivalConnector.put(any(), any())(any(), any(), any()))
       .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/<>"))) ))

     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must escape arrival ID in Location response header" in {
     when(mockArrivalConnector.put(any(), any())(any(), any(), any()))
       .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123-@+*~-31@"))) ))

     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> routes.ArrivalMovementController.getArrival("123-@+*~-31@").urlWithContext)
   }

   "must exclude query string if present in downstream Location header" in {
     when(mockArrivalConnector.put(any(), any())(any(), any(), any()))
       .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123?status=success"))) ))

     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> routes.ArrivalMovementController.getArrival("123").urlWithContext)
   }

   "must return UnsupportedMediaType when Content-Type is JSON" in {
     val request = FakeRequest(method = "PUT", uri = routes.ArrivalMovementController.resubmitArrivalNotification("123").url, headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json")), body = AnyContentAsEmpty)

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return UnsupportedMediaType when no Content-Type specified" in {
     val request = fakeRequestArrivals(method = "PUT", uri = routes.ArrivalMovementController.resubmitArrivalNotification("123").url, headers = FakeHeaders(), body = AnyContentAsEmpty)

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return UnsupportedMediaType when empty XML payload is sent" in {
     val request = fakeRequestArrivals(method = "PUT", uri = routes.ArrivalMovementController.resubmitArrivalNotification("123").url, headers = FakeHeaders(), body = AnyContentAsEmpty)

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return BadRequest when invalid XML payload is sent" in {
     val request = fakeRequestArrivals(method = "PUT", uri = routes.ArrivalMovementController.resubmitArrivalNotification("123").url, body = InvalidCC007A)

     val result = route(app, request).value

     status(result) mustBe BAD_REQUEST
   }
 }

 "GET /movements/arrivals/:arrivalId" - {
   "return 200 with json body of arrival" in {
     when(mockArrivalConnector.get(any())(any(), any(), any()))
       .thenReturn(Future.successful(Right(sourceArrival)))

     val request = FakeRequest("GET", routes.ArrivalMovementController.getArrival("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
     val result = route(app, request).value

     status(result) mustBe OK
     contentAsString(result) mustEqual expectedArrivalResult.toString()
   }

   "return 404 if downstream return 404" in {
     when(mockArrivalConnector.get(any())(any(), any(), any()))
       .thenReturn(Future.successful(Left(HttpResponse(404, ""))))

     val request = FakeRequest("GET", routes.ArrivalMovementController.getArrival("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
     val result = route(app, request).value

     status(result) mustBe NOT_FOUND
   }

   "return 500 for other downstream errors" in {
     when(mockArrivalConnector.get(any())(any(), any(), any()))
       .thenReturn(Future.successful(Left(HttpResponse(INTERNAL_SERVER_ERROR, ""))))

     val request = FakeRequest("GET", routes.ArrivalMovementController.getArrival("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

 }

 "GET /movements/arrivals/" - {

   "return 200 with json body of a sequence of arrivals" in {
     when(mockArrivalConnector.getForEori()(any(), any(), any()))
       .thenReturn(Future.successful(Right(Arrivals(Seq(sourceArrival, sourceArrival, sourceArrival)))))

     val request = FakeRequest("GET", routes.ArrivalMovementController.getArrivalsForEori.url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
     val result = route(app, request).value

     status(result) mustBe OK
     contentAsString(result) mustEqual Json.toJson(ResponseArrivals(Seq(expectedArrival, expectedArrival, expectedArrival))).toString()
   }

   "return 200 with empty list if that is provided" in {
     when(mockArrivalConnector.getForEori()(any(), any(), any()))
       .thenReturn(Future.successful(Right(Arrivals(Nil))))

     val request = FakeRequest("GET", routes.ArrivalMovementController.getArrivalsForEori.url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
     val result = route(app, request).value

     status(result) mustBe OK
     contentAsString(result) mustEqual Json.toJson(ResponseArrivals(Nil)).toString()
   }

   "return 500 for downstream errors" in {
     when(mockArrivalConnector.getForEori()(any(), any(), any()))
       .thenReturn(Future.successful(Left(HttpResponse(INTERNAL_SERVER_ERROR, ""))))

     val request = FakeRequest("GET", routes.ArrivalMovementController.getArrivalsForEori.url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }
 }
}
