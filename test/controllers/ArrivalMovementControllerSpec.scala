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

import scala.xml.NodeSeq
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

  def ctcFakeRequest() = FakeRequest(method = "POST", uri = "/movements/arrivals", headers = FakeHeaders(), body = AnyContentAsEmpty)

  def ctcFakeRequestXML() =
    FakeRequest(method = "POST", uri = "/movements/arrivals", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml", HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+xml")), body = AnyContentAsEmpty)

  def ctcFakeRequestXML(body: NodeSeq) =
    FakeRequest(method = "POST", uri = "/movements/arrivals", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml", HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+xml")), body)

  def ctcFakeRequestXML(body: NodeSeq, uri: String, verb: String) =
    FakeRequest(verb, uri, headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml", HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+xml")), body)

 "POST /movements/arrivals" - {
   "must return Accepted when successful" in {
     when(mockArrivalConnector.post(any())(any(), any()))
       .thenReturn(Future.successful(HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/arrivals/123")), responseString = None) ))

     val request = ctcFakeRequestXML(CC007A)
     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123")
   }

   "must return InternalServerError when unsuccessful" in {
     when(mockArrivalConnector.post(any())(any(), any()))
       .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR)))

     val request = ctcFakeRequestXML(CC007A)
     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when no Location in downstream response header" in {
     when(mockArrivalConnector.post(any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(), responseString = None) ))

     val request = ctcFakeRequestXML(CC007A)
     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when invalid Location value in downstream response header" in {
     when(mockArrivalConnector.post(any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/arrivals/<>")), responseString = None) ))

     val request = ctcFakeRequestXML(CC007A)
     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must escape arrival ID in Location response header" in {
     when(mockArrivalConnector.post(any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/arrivals/123-@+*~-31@")), responseString = None) ))

     val request = ctcFakeRequestXML(CC007A)
     val result = route(app, request).value
     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123-%40%2B*%7E-31%40")
   }

   "must exclude query string if present in downstream Location header" in {
     when(mockArrivalConnector.post(any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/arrivals/123?status=success")), responseString = None) ))

     val request = ctcFakeRequestXML(CC007A)
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
     val request = ctcFakeRequest().withRawBody(ByteString("body"))

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return UnsupportedMediaType when empty XML payload is sent" in {
     val request = ctcFakeRequest()

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return BadRequest when invalid XML payload is sent" in {
     val request = ctcFakeRequestXML(<message>007</message>)

     val result = route(app, request).value

     status(result) mustBe BAD_REQUEST
   }

   "must return BadRequest when XML payload is missing elements" in {
     val request = ctcFakeRequestXML(<CC007A>
       <SynIdeMES1>UNOC</SynIdeMES1>
       <SynVerNumMES2>3</SynVerNumMES2>
       <MesSenMES3>LOCAL-eori</MesSenMES3>
       <MesRecMES6>NCTS</MesRecMES6>
       <DatOfPreMES9>20200204</DatOfPreMES9>
       <TimOfPreMES10>1302</TimOfPreMES10>
       <IntConRefMES11>WE202002046</IntConRefMES11>
       <AppRefMES14>NCTS</AppRefMES14>
       <TesIndMES18>0</TesIndMES18>
       <MesIdeMES19>1</MesIdeMES19>
       <MesTypMES20>GB007A</MesTypMES20>
       <HEAHEA>
         <DocNumHEA5>99IT9876AB88901209</DocNumHEA5>
         <CusSubPlaHEA66>EXAMPLE1</CusSubPlaHEA66>
         <ArrNotPlaHEA60>NW16XE</ArrNotPlaHEA60>
         <ArrNotPlaHEA60LNG>EN</ArrNotPlaHEA60LNG>
         <ArrAgrLocOfGooHEA63LNG>EN</ArrAgrLocOfGooHEA63LNG>
         <SimProFlaHEA132>0</SimProFlaHEA132>
         <ArrNotDatHEA141>20200204</ArrNotDatHEA141>
       </HEAHEA>
       <TRADESTRD>
         <NamTRD7>EXAMPLE2</NamTRD7>
         <StrAndNumTRD22>Baker Street</StrAndNumTRD22>
         <PosCodTRD23>NW16XE</PosCodTRD23>
         <CitTRD24>London</CitTRD24>
         <CouTRD25>GB</CouTRD25>
         <NADLNGRD>EN</NADLNGRD>
         <TINTRD59>EXAMPLE3</TINTRD59>
       </TRADESTRD>
     </CC007A>)

     val result = route(app, request).value

     status(result) mustBe BAD_REQUEST
   }
 }

 "PUT /movements/arrivals/:arrivalId" - {

   val request = ctcFakeRequestXML(CC007A, "/movements/arrivals/123" ,"PUT")

   "must return Accepted when successful" in {
     when(mockArrivalConnector.put(any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/arrivals/123")), responseString = None) ))

     val result = route(app, request).value

     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123")
   }

   "must return InternalServerError when unsuccessful" in {
     when(mockArrivalConnector.put(any(), any())(any(), any()))
       .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR)))

     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when no Location in downstream response header" in {
     when(mockArrivalConnector.put(any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(), responseString = None) ))

     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must return InternalServerError when invalid Location value in downstream response header" in {
     when(mockArrivalConnector.put(any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/arrivals/1/messages/<>")), responseString = None) ))

     val result = route(app, request).value

     status(result) mustBe INTERNAL_SERVER_ERROR
   }

   "must escape arrival ID in Location response header" in {
     when(mockArrivalConnector.put(any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/arrivals/123-@+*~-31@/messages/123-@+*~-31@")), responseString = None) ))

     val result = route(app, request).value
     status(result) mustBe ACCEPTED
     headers(result) must contain (LOCATION -> "/customs/transits/movements/arrivals/123-%40%2B*%7E-31%40")
   }

   "must exclude query string if present in downstream Location header" in {
     when(mockArrivalConnector.put(any(), any())(any(), any()))
       .thenReturn(Future.successful( HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/arrivals/123?status=success")), responseString = None) ))

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
     val request = FakeRequest(method = "PUT", uri = "/movements/arrivals/123", headers = FakeHeaders(), body = AnyContentAsEmpty)
       .withRawBody(ByteString("body"))

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return UnsupportedMediaType when empty XML payload is sent" in {
     val request = FakeRequest(method = "PUT", uri = "/movements/arrivals/123", headers = FakeHeaders(), body = AnyContentAsEmpty)

     val result = route(app, request).value

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return BadRequest when invalid XML payload is sent" in {
     val body = <abc>123</abc>

     val request = ctcFakeRequestXML(body, "/movements/arrivals/123" ,"PUT")

     val result = route(app, request).value

     status(result) mustBe BAD_REQUEST
   }

   "must return BadRequest when XML payload is missing elements" in {
     val body = <CC007A>
       <SynIdeMES1>UNOC</SynIdeMES1>
       <SynVerNumMES2>3</SynVerNumMES2>
       <MesSenMES3>LOCAL-eori</MesSenMES3>
       <MesRecMES6>NCTS</MesRecMES6>
       <DatOfPreMES9>20200204</DatOfPreMES9>
       <TimOfPreMES10>1302</TimOfPreMES10>
       <IntConRefMES11>WE202002046</IntConRefMES11>
       <AppRefMES14>NCTS</AppRefMES14>
       <TesIndMES18>0</TesIndMES18>
       <MesIdeMES19>1</MesIdeMES19>
       <MesTypMES20>GB007A</MesTypMES20>
       <HEAHEA>
         <DocNumHEA5>99IT9876AB88901209</DocNumHEA5>
         <CusSubPlaHEA66>EXAMPLE1</CusSubPlaHEA66>
         <ArrNotPlaHEA60>NW16XE</ArrNotPlaHEA60>
         <ArrNotPlaHEA60LNG>EN</ArrNotPlaHEA60LNG>
         <ArrAgrLocOfGooHEA63LNG>EN</ArrAgrLocOfGooHEA63LNG>
         <SimProFlaHEA132>0</SimProFlaHEA132>
         <ArrNotDatHEA141>20200204</ArrNotDatHEA141>
       </HEAHEA>
       <TRADESTRD>
         <NamTRD7>EXAMPLE2</NamTRD7>
         <StrAndNumTRD22>Baker Street</StrAndNumTRD22>
         <PosCodTRD23>NW16XE</PosCodTRD23>
         <CitTRD24>London</CitTRD24>
         <CouTRD25>GB</CouTRD25>
         <NADLNGRD>EN</NADLNGRD>
         <TINTRD59>EXAMPLE3</TINTRD59>
       </TRADESTRD>
     </CC007A>

     val request = ctcFakeRequestXML(body, "/movements/arrivals/123" ,"PUT")

     val result = route(app, request).value

     status(result) mustBe BAD_REQUEST
   }
 }

 }
