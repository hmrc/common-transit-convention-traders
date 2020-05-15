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

import controllers.actions.{AuthAction, FakeAuthAction}
import akka.util.ByteString
import connectors.ArrivalConnector
import data.TestXml
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
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.Future

class ArrivalMovementControllerSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach with TestXml {
  private val mockArrivalConnector: ArrivalConnector = mock[ArrivalConnector]

  override lazy val app = GuiceApplicationBuilder()
    .overrides(bind[AuthAction].to[FakeAuthAction])
    .overrides(bind[ArrivalConnector].toInstance(mockArrivalConnector))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockArrivalConnector)
  }

  def fakeRequestArrivals[A](method: String, headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), uri: String = "/movements/arrivals", body: A) =
    FakeRequest(method = method, uri = uri, headers, body = body)

 "POST /movements/arrivals" - {
   "must return Accepted when successful" in {
     when(mockArrivalConnector.post(any(), any())(any(), any()))
       .thenReturn(Future.successful(HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123")), responseString = None) ))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123")
   }

   "must return InternalServerError when unsuccessful" in {
     when(mockArrivalConnector.post(any(), any())(any(), any()))
       .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR)))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when no Location in downstream response header" in {
     when(mockArrivalConnector.post(any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(), responseString = None) ))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when invalid Location value in downstream response header" ignore {
     when(mockArrivalConnector.post(any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/<>")), responseString = None) ))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must escape arrival ID in Location response header" in {
     when(mockArrivalConnector.post(any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123-@+*~-31@")), responseString = None) ))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123-%40%2B*%7E-31%40")
   }

   "must exclude query string if present in downstream Location header" in {
     when(mockArrivalConnector.post(any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123?status=success")), responseString = None) ))

     val request = fakeRequestArrivals(method = "POST", body = CC007A)
     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123")
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

   val request = fakeRequestArrivals(method = "PUT", uri = "/movements/arrivals/123", body = CC007A)

   "must return Accepted when successful" in {
     when(mockArrivalConnector.put(any(), any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123")), responseString = None) ))

     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123")
   }

   "must return InternalServerError when unsuccessful" in {
     when(mockArrivalConnector.put(any(), any(), any())(any(), any()))
       .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR)))

     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when no Location in downstream response header" in {
     when(mockArrivalConnector.put(any(), any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(), responseString = None) ))

     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when invalid Location value in downstream response header" ignore {
     when(mockArrivalConnector.put(any(), any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/<>")), responseString = None) ))

     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must escape arrival ID in Location response header" in {
     when(mockArrivalConnector.put(any(), any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123-@+*~-31@/messages/123-@+*~-31@")), responseString = None) ))

     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123-%40%2B*%7E-31%40")
   }

   "must exclude query string if present in downstream Location header" in {
     when(mockArrivalConnector.put(any(), any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123?status=success")), responseString = None) ))

     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123")
   }

   "must return UnsupportedMediaType when Content-Type is JSON" in {
     val request = FakeRequest(method = "PUT", uri = "/movements/arrivals/123", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json")), body = AnyContentAsEmpty)

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return UnsupportedMediaType when no Content-Type specified" in {
     val request = fakeRequestArrivals(method = "PUT", uri = "/movements/arrivals/123", headers = FakeHeaders(), body = AnyContentAsEmpty)

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return UnsupportedMediaType when empty XML payload is sent" in {
     val request = fakeRequestArrivals(method = "PUT", uri = "/movements/arrivals/123", headers = FakeHeaders(), body = AnyContentAsEmpty)

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return BadRequest when invalid XML payload is sent" in {
     val request = fakeRequestArrivals(method = "PUT", uri = "/movements/arrivals/123", body = InvalidCC007A)

     val result = route(app, request).value

     status(result) mustBe BAD_REQUEST
   }
 }
}
