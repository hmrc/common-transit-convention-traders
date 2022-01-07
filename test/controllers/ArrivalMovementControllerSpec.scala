/*
 * Copyright 2022 HM Revenue & Customs
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
import connectors.ArrivalConnector
import connectors.ResponseHeaders
import controllers.actions.AuthAction
import controllers.actions.FakeAuthAction
import data.TestXml
import models.Box
import models.domain.Arrival
import models.domain.ArrivalId
import models.domain.Arrivals
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
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.headers
import play.api.test.Helpers._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.CallOps._
import utils.TestMetrics

import scala.concurrent.Future

class ArrivalMovementControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterEach
    with TestXml {
  private val mockArrivalConnector: ArrivalConnector = mock[ArrivalConnector]

  override lazy val app = GuiceApplicationBuilder()
    .overrides(
      bind[Metrics].toInstance(new TestMetrics),
      bind[AuthAction].to[FakeAuthAction],
      bind[ArrivalConnector].toInstance(mockArrivalConnector)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockArrivalConnector)
  }

  val sourceArrival = Arrival(
    ArrivalId(123),
    routes.ArrivalMovementController.getArrival(ArrivalId(123)).urlWithContext,
    routes.ArrivalMessagesController.getArrivalMessages(ArrivalId(123)).urlWithContext,
    "MRN",
    LocalDateTime.of(2020, 2, 2, 2, 2, 2),
    LocalDateTime.of(2020, 2, 2, 2, 2, 2)
  )

  val expectedArrivalResult = Json.parse("""
      |{
      |  "id": "123",
      |  "created": "2020-02-02T02:02:02",
      |  "updated": "2020-02-02T02:02:02",
      |  "movementReferenceNumber": "MRN",
      |  "_links": {
      |    "self": {
      |      "href": "/customs/transits/movements/arrivals/123"
      |    },
      |    "messages": {
      |      "href": "/customs/transits/movements/arrivals/123/messages"
      |    }
      |  }
      |}""".stripMargin)

  def fakeRequestArrivals[A](
    method: String,
    headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")),
    uri: String = routes.ArrivalMovementController.createArrivalNotification().url,
    body: A
  ) =
    FakeRequest(method = method, uri = uri, headers, body = body)

  def responseHeaders(location: String) = ResponseHeaders(Map(LOCATION -> Seq(location)), Option.empty[Box])
  val emptyHeaders                      = ResponseHeaders(Map.empty, Option.empty[Box])

  "POST /movements/arrivals" - {

    val expectedJson = Json.parse("""
       |{
       |  "_links": {
       |    "self": {
       |      "href": "/customs/transits/movements/arrivals/123"
       |    }
       |  },
       |  "arrivalId": "123",
       |  "messageType": "IE007",
       |  "body": "<CC007A>\n    <SynIdeMES1>UNOC</SynIdeMES1>\n    <SynVerNumMES2>3</SynVerNumMES2>\n    <MesRecMES6>NCTS</MesRecMES6>\n    <DatOfPreMES9>20200204</DatOfPreMES9>\n    <TimOfPreMES10>1302</TimOfPreMES10>\n    <IntConRefMES11>WE202002046</IntConRefMES11>\n    <AppRefMES14>NCTS</AppRefMES14>\n    <TesIndMES18>0</TesIndMES18>\n    <MesIdeMES19>1</MesIdeMES19>\n    <MesTypMES20>GB007A</MesTypMES20>\n    <HEAHEA>\n      <DocNumHEA5>99IT9876AB88901209</DocNumHEA5>\n      <CusSubPlaHEA66>EXAMPLE1</CusSubPlaHEA66>\n      <ArrNotPlaHEA60>NW16XE</ArrNotPlaHEA60>\n      <ArrNotPlaHEA60LNG>EN</ArrNotPlaHEA60LNG>\n      <ArrAgrLocOfGooHEA63LNG>EN</ArrAgrLocOfGooHEA63LNG>\n      <SimProFlaHEA132>0</SimProFlaHEA132>\n      <ArrNotDatHEA141>20200204</ArrNotDatHEA141>\n    </HEAHEA>\n    <TRADESTRD>\n      <NamTRD7>EXAMPLE2</NamTRD7>\n      <StrAndNumTRD22>Baker Street</StrAndNumTRD22>\n      <PosCodTRD23>NW16XE</PosCodTRD23>\n      <CitTRD24>London</CitTRD24>\n      <CouTRD25>GB</CouTRD25>\n      <NADLNGRD>EN</NADLNGRD>\n      <TINTRD59>EXAMPLE3</TINTRD59>\n    </TRADESTRD>\n    <CUSOFFPREOFFRES>\n      <RefNumRES1>GB000128</RefNumRES1>\n    </CUSOFFPREOFFRES>\n  </CC007A>"
       |}""".stripMargin)

    "must return Accepted when successful" in {
      when(mockArrivalConnector.post(any())(any(), any()))
        .thenReturn(Future.successful(Right(responseHeaders("/transit-movements-trader-at-destination/movements/arrivals/123"))))

      val request = fakeRequestArrivals(method = "POST", body = CC007A)
      val result  = route(app, request).value

      status(result) mustBe ACCEPTED
      contentAsJson(result) mustBe expectedJson
      headers(result) must contain(LOCATION -> routes.ArrivalMovementController.getArrival(ArrivalId(123)).urlWithContext)
    }

    "must return BadRequest when containing MesSenMES3" in {
      val request = fakeRequestArrivals(method = "POST", body = CC007AwithMesSenMES3)
      val result  = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }

    "must return InternalServerError when unsuccessful" in {
      val errorResponse = UpstreamErrorResponse("test error message", INTERNAL_SERVER_ERROR)
      when(mockArrivalConnector.post(any())(any(), any()))
        .thenReturn(Future.successful(Left(errorResponse)))

      val request = fakeRequestArrivals(method = "POST", body = CC007A)
      val result  = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when no Location in downstream response header" in {
      when(mockArrivalConnector.post(any())(any(), any()))
        .thenReturn(Future.successful(Right(emptyHeaders)))

      val request = fakeRequestArrivals(method = "POST", body = CC007A)
      val result  = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when invalid Location value in downstream response header" ignore {
      when(mockArrivalConnector.post(any())(any(), any()))
        .thenReturn(Future.successful(Right(responseHeaders("/transit-movements-trader-at-destination/movements/arrivals/<>"))))

      val request = fakeRequestArrivals(method = "POST", body = CC007A)
      val result  = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must exclude query string if present in downstream Location header" in {
      when(mockArrivalConnector.post(any())(any(), any()))
        .thenReturn(Future.successful(Right(responseHeaders("/transit-movements-trader-at-destination/movements/arrivals/123?status=success"))))

      val request = fakeRequestArrivals(method = "POST", body = CC007A)
      val result  = route(app, request).value

      status(result) mustBe ACCEPTED
      contentAsJson(result) mustBe expectedJson
      headers(result) must contain(LOCATION -> routes.ArrivalMovementController.getArrival(ArrivalId(123)).urlWithContext)
    }

    "must return UnsupportedMediaType when Content-Type is JSON" in {
      val request = FakeRequest(
        method = "POST",
        uri = "/movements/arrivals",
        headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json")),
        body = AnyContentAsEmpty
      )

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

    val expectedJson = Json.parse("""
       |{
       |  "_links": {
       |    "self": {
       |      "href": "/customs/transits/movements/arrivals/123"
       |    }
       |  },
       |  "arrivalId": "123",
       |  "messageType": "IE007",
       |  "body": "<CC007A>\n    <SynIdeMES1>UNOC</SynIdeMES1>\n    <SynVerNumMES2>3</SynVerNumMES2>\n    <MesRecMES6>NCTS</MesRecMES6>\n    <DatOfPreMES9>20200204</DatOfPreMES9>\n    <TimOfPreMES10>1302</TimOfPreMES10>\n    <IntConRefMES11>WE202002046</IntConRefMES11>\n    <AppRefMES14>NCTS</AppRefMES14>\n    <TesIndMES18>0</TesIndMES18>\n    <MesIdeMES19>1</MesIdeMES19>\n    <MesTypMES20>GB007A</MesTypMES20>\n    <HEAHEA>\n      <DocNumHEA5>99IT9876AB88901209</DocNumHEA5>\n      <CusSubPlaHEA66>EXAMPLE1</CusSubPlaHEA66>\n      <ArrNotPlaHEA60>NW16XE</ArrNotPlaHEA60>\n      <ArrNotPlaHEA60LNG>EN</ArrNotPlaHEA60LNG>\n      <ArrAgrLocOfGooHEA63LNG>EN</ArrAgrLocOfGooHEA63LNG>\n      <SimProFlaHEA132>0</SimProFlaHEA132>\n      <ArrNotDatHEA141>20200204</ArrNotDatHEA141>\n    </HEAHEA>\n    <TRADESTRD>\n      <NamTRD7>EXAMPLE2</NamTRD7>\n      <StrAndNumTRD22>Baker Street</StrAndNumTRD22>\n      <PosCodTRD23>NW16XE</PosCodTRD23>\n      <CitTRD24>London</CitTRD24>\n      <CouTRD25>GB</CouTRD25>\n      <NADLNGRD>EN</NADLNGRD>\n      <TINTRD59>EXAMPLE3</TINTRD59>\n    </TRADESTRD>\n    <CUSOFFPREOFFRES>\n      <RefNumRES1>GB000128</RefNumRES1>\n    </CUSOFFPREOFFRES>\n  </CC007A>"
       |}""".stripMargin)

    val request = fakeRequestArrivals(method = "PUT", uri = routes.ArrivalMovementController.resubmitArrivalNotification(ArrivalId(123)).url, body = CC007A)

    "must return Accepted when successful" in {
      when(mockArrivalConnector.put(any(), ArrivalId(any()))(any(), any()))
        .thenReturn(Future.successful(Right(responseHeaders("/transit-movements-trader-at-destination/movements/arrivals/123"))))

      val result = route(app, request).value

      status(result) mustBe ACCEPTED
      contentAsJson(result) mustBe expectedJson
      headers(result) must contain(LOCATION -> routes.ArrivalMovementController.getArrival(ArrivalId(123)).urlWithContext)
    }

    "must return InternalServerError when unsuccessful" in {
      val errorResponse = UpstreamErrorResponse("test error message", INTERNAL_SERVER_ERROR)
      when(mockArrivalConnector.put(any(), ArrivalId(any()))(any(), any()))
        .thenReturn(Future.successful(Left(errorResponse)))

      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when no Location in downstream response header" in {
      when(mockArrivalConnector.put(any(), ArrivalId(any()))(any(), any()))
        .thenReturn(Future.successful(Right(emptyHeaders)))

      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when invalid Location value in downstream response header" ignore {
      when(mockArrivalConnector.put(any(), ArrivalId(any()))(any(), any()))
        .thenReturn(Future.successful(Right(responseHeaders("/transit-movements-trader-at-destination/movements/arrivals/<>"))))

      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must exclude query string if present in downstream Location header" in {
      when(mockArrivalConnector.put(any(), ArrivalId(any()))(any(), any()))
        .thenReturn(Future.successful(Right(responseHeaders("/transit-movements-trader-at-destination/movements/arrivals/123?status=success"))))

      val result = route(app, request).value

      status(result) mustBe ACCEPTED
      contentAsJson(result) mustBe expectedJson
      headers(result) must contain(LOCATION -> routes.ArrivalMovementController.getArrival(ArrivalId(123)).urlWithContext)
    }

    "must return UnsupportedMediaType when Content-Type is JSON" in {
      val request = FakeRequest(
        method = "PUT",
        uri = routes.ArrivalMovementController.resubmitArrivalNotification(ArrivalId(123)).url,
        headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json")),
        body = AnyContentAsEmpty
      )

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when no Content-Type specified" in {
      val request = fakeRequestArrivals(
        method = "PUT",
        uri = routes.ArrivalMovementController.resubmitArrivalNotification(ArrivalId(123)).url,
        headers = FakeHeaders(),
        body = AnyContentAsEmpty
      )

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when empty XML payload is sent" in {
      val request = fakeRequestArrivals(
        method = "PUT",
        uri = routes.ArrivalMovementController.resubmitArrivalNotification(ArrivalId(123)).url,
        headers = FakeHeaders(),
        body = AnyContentAsEmpty
      )

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return BadRequest when invalid XML payload is sent" in {
      val request =
        fakeRequestArrivals(method = "PUT", uri = routes.ArrivalMovementController.resubmitArrivalNotification(ArrivalId(123)).url, body = InvalidCC007A)

      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }
  }

  "GET /movements/arrivals/:arrivalId" - {
    "return 200 with json body of arrival" in {
      when(mockArrivalConnector.get(ArrivalId(any()))(any(), any()))
        .thenReturn(Future.successful(Right(sourceArrival)))

      val request = FakeRequest(
        "GET",
        routes.ArrivalMovementController.getArrival(ArrivalId(123)).url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedArrivalResult.toString()
    }

    "return 404 if downstream return 404" in {
      when(mockArrivalConnector.get(ArrivalId(any()))(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(404, ""))))

      val request = FakeRequest(
        "GET",
        routes.ArrivalMovementController.getArrival(ArrivalId(123)).url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe NOT_FOUND
    }

    "return 500 for other downstream errors" in {
      when(mockArrivalConnector.get(ArrivalId(any()))(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(INTERNAL_SERVER_ERROR, ""))))

      val request = FakeRequest(
        "GET",
        routes.ArrivalMovementController.getArrival(ArrivalId(123)).url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

  }

  "GET /movements/arrivals/" - {

    "return 200 with json body of a sequence of arrivals" in {
      when(mockArrivalConnector.getForEori(any())(any(), any()))
        .thenReturn(Future.successful(Right(Arrivals(Seq(sourceArrival, sourceArrival, sourceArrival), 3, 3))))

      val request = FakeRequest(
        "GET",
        routes.ArrivalMovementController.getArrivalsForEori(None).url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      val expectedJson = Json.parse("""
         |{
         |  "_links": {
         |    "self": {
         |      "href": "/customs/transits/movements/arrivals"
         |    }
         |  },
         |  "_embedded": {
         |    "arrivals": [
         |      {
         |        "id": "123",
         |        "created": "2020-02-02T02:02:02",
         |        "updated": "2020-02-02T02:02:02",
         |        "movementReferenceNumber": "MRN",
         |        "_links": {
         |          "self": {
         |            "href": "/customs/transits/movements/arrivals/123"
         |          },
         |          "messages": {
         |            "href": "/customs/transits/movements/arrivals/123/messages"
         |          }
         |        }
         |      },
         |      {
         |        "id": "123",
         |        "created": "2020-02-02T02:02:02",
         |        "updated": "2020-02-02T02:02:02",
         |        "movementReferenceNumber": "MRN",
         |        "_links": {
         |          "self": {
         |            "href": "/customs/transits/movements/arrivals/123"
         |          },
         |          "messages": {
         |            "href": "/customs/transits/movements/arrivals/123/messages"
         |          }
         |        }
         |      },
         |      {
         |        "id": "123",
         |        "created": "2020-02-02T02:02:02",
         |        "updated": "2020-02-02T02:02:02",
         |        "movementReferenceNumber": "MRN",
         |        "_links": {
         |          "self": {
         |            "href": "/customs/transits/movements/arrivals/123"
         |          },
         |          "messages": {
         |            "href": "/customs/transits/movements/arrivals/123/messages"
         |          }
         |        }
         |      }
         |    ],
         |    "retrievedArrivals": 3,
         |    "totalArrivals": 3
         |  }
         |}""".stripMargin)

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedJson.toString()
    }

    "return 200 with empty list if that is provided" in {
      when(mockArrivalConnector.getForEori(any())(any(), any()))
        .thenReturn(Future.successful(Right(Arrivals(Nil, 0, 0))))

      val request = FakeRequest(
        "GET",
        routes.ArrivalMovementController.getArrivalsForEori(None).url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      val expectedJson = Json.parse("""
         |{
         |  "_links": {
         |    "self": {
         |      "href": "/customs/transits/movements/arrivals"
         |    }
         |  },
         |  "_embedded": {
         |    "arrivals": [],
         |    "retrievedArrivals": 0,
         |    "totalArrivals": 0
         |  }
         |}""".stripMargin)

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedJson.toString()
    }

    "pass updatedSince parameter on to connector" in {
      val argCaptor = ArgumentCaptor.forClass(classOf[Option[OffsetDateTime]])
      val dateTime  = Some(OffsetDateTime.of(2021, 6, 23, 12, 1, 24, 0, ZoneOffset.UTC))

      when(mockArrivalConnector.getForEori(argCaptor.capture())(any(), any()))
        .thenReturn(Future.successful(Right(Arrivals(Nil, 0, 0))))

      val request = FakeRequest(
        "GET",
        routes.ArrivalMovementController.getArrivalsForEori(dateTime).url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe OK
      argCaptor.getValue() mustBe dateTime
    }

    "return 500 for downstream errors" in {
      when(mockArrivalConnector.getForEori(any())(any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(INTERNAL_SERVER_ERROR, ""))))

      val request = FakeRequest(
        "GET",
        routes.ArrivalMovementController.getArrivalsForEori(None).url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")),
        AnyContentAsEmpty
      )
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }
}
