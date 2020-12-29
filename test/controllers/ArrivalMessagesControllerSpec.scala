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
import connectors.ArrivalMessageConnector
import controllers.actions.{AuthAction, FakeAuthAction}
import data.TestXml
import models.domain.{ArrivalWithMessages, MovementMessage}
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

class ArrivalMessagesControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach with TestXml {
  private val mockMessageConnector: ArrivalMessageConnector = mock[ArrivalMessageConnector]

  override lazy val app = GuiceApplicationBuilder()
    .overrides(bind[AuthAction].to[FakeAuthAction])
    .overrides(bind[ArrivalMessageConnector].toInstance(mockMessageConnector))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMessageConnector)
  }

  val sourceMovement = MovementMessage(
    routes.ArrivalMessagesController.getArrivalMessage("123","4").urlWithContext,
    LocalDateTime.of(2020, 2, 2, 2, 2, 2),
    "IE025",
    <test>default</test>)

  val sourceArrival = ArrivalWithMessages(123, routes.ArrivalMovementController.getArrival("123").urlWithContext, routes.ArrivalMessagesController.getArrivalMessages("123").urlWithContext, "MRN", "status", LocalDateTime.of(2020, 2, 2, 2, 2, 2), LocalDateTime.of(2020, 2, 2, 2, 2, 2), Seq(sourceMovement, sourceMovement))

  val json = Json.toJson[MovementMessage](sourceMovement)

  val expectedMessageResult = Json.parse(
    """
      |{
      |  "_links": [
      |    {
      |      "self": {
      |        "href": "/customs/transits/movements/arrivals/123/messages/4"
      |      }
      |    },
      |    {
      |      "arrival": {
      |        "href": "/customs/transits/movements/arrivals/123"
      |      }
      |    }
      |  ],
      |  "arrivalId": "123",
      |  "messageId": "4",
      |  "received": "2020-02-02T02:02:02",
      |  "messageType": "IE025",
      |  "body": "<test>default</test>"
      |}
      |""".stripMargin)


  val expectedArrivalResult = Json.parse(
    """
      |{
      |  "_links": [
      |    {
      |      "self": {
      |        "href": "/customs/transits/movements/arrivals/123/messages"
      |      }
      |    }
      |  ],
      |  "_embedded": [
      |    {
      |      "messages": [
      |        {
      |          "_links": [
      |            {
      |              "self": {
      |                "href": "/customs/transits/movements/arrivals/123/messages/4"
      |              }
      |            },
      |            {
      |              "arrival": {
      |                "href": "/customs/transits/movements/arrivals/123"
      |              }
      |            }
      |          ],
      |          "arrivalId": "123",
      |          "messageId": "4",
      |          "received": "2020-02-02T02:02:02",
      |          "messageType": "IE025",
      |          "body": "<test>default</test>"
      |        },
      |        {
      |          "_links": [
      |            {
      |              "self": {
      |                "href": "/customs/transits/movements/arrivals/123/messages/4"
      |              }
      |            },
      |            {
      |              "arrival": {
      |                "href": "/customs/transits/movements/arrivals/123"
      |              }
      |            }
      |          ],
      |          "arrivalId": "123",
      |          "messageId": "4",
      |          "received": "2020-02-02T02:02:02",
      |          "messageType": "IE025",
      |          "body": "<test>default</test>"
      |        }
      |      ]
      |    },
      |    {
      |      "arrival": {
      |        "id": "123",
      |        "created": "2020-02-02T02:02:02",
      |        "updated": "2020-02-02T02:02:02",
      |        "movementReferenceNumber": "MRN",
      |        "status": "status",
      |        "_links": [
      |          {
      |            "self": {
      |              "href": "/customs/transits/movements/arrivals/123"
      |            }
      |          },
      |          {
      |            "messages": {
      |              "href": "/customs/transits/movements/arrivals/123/messages"
      |            }
      |          }
      |        ]
      |      }
      |    }
      |  ]
      |}""".stripMargin)

  def fakeRequestMessages[A](method: String, headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), uri: String, body: A) =
    FakeRequest(method = method, uri = uri, headers, body = body)

  "GET /movements/arrivals/:arrivalId/messages/:messageId" - {
    "return 200 and Message" in {
      when(mockMessageConnector.get(any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(Right(sourceMovement)))

      val request = FakeRequest("GET", routes.ArrivalMessagesController.getArrivalMessage("123","4").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      contentAsString(result) mustEqual expectedMessageResult.toString()
      status(result) mustBe OK
    }

    "return 400 if the downstream returns 400" in {
      when(mockMessageConnector.get(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(400, ""))))

      val request = FakeRequest("GET", routes.ArrivalMessagesController.getArrivalMessage("123","4").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }

    "return 400 with body if the downstream returns 400 with body" in {
      when(mockMessageConnector.get(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(400, "abc"))))

      val request = FakeRequest("GET", routes.ArrivalMessagesController.getArrivalMessage("123","4").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
      contentAsString(result) mustBe "abc"
    }

    "return 404 if the downstream returns 404" in {
      when(mockMessageConnector.get(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(404, ""))))

      val request = FakeRequest("GET", routes.ArrivalMessagesController.getArrivalMessage("123","4").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe NOT_FOUND
    }

    "return 500 for other downstream errors" in {
      when(mockMessageConnector.get(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse.apply(INTERNAL_SERVER_ERROR, json, Headers.create().toMap) )))

      val request = FakeRequest("GET", routes.ArrivalMessagesController.getArrivalMessage("123","4").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "POST /movements/arrivals/:arrivalId/messages" - {
    "must return Accepted when successful" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/1"))) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.ArrivalMessagesController.sendMessageDownstream("123").url, body = CC044A)
      val result = route(app, request).value

      val expectedJson = Json.parse(
        """
          |{
          |  "_links": [
          |    {
          |      "self": {
          |        "href": "/customs/transits/movements/arrivals/123/messages/1"
          |      }
          |    },
          |    {
          |      "arrival": {
          |        "href": "/customs/transits/movements/arrivals/123"
          |      }
          |    }
          |  ],
          |  "arrivalId": "123",
          |  "messageId": "1",
          |  "messageType": "IE044",
          |  "body": "<CC044A>\n      <SynIdeMES1>tval</SynIdeMES1>\n      <SynVerNumMES2>1</SynVerNumMES2>\n      \n      <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>\n      <MesRecMES6>111111</MesRecMES6>\n      \n      <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>\n      <DatOfPreMES9>20001001</DatOfPreMES9>\n      <TimOfPreMES10>1111</TimOfPreMES10>\n      <IntConRefMES11>111111</IntConRefMES11>\n      \n      <RecRefMES12>111111</RecRefMES12>\n      \n      <RecRefQuaMES13>to</RecRefQuaMES13>\n      \n      <AppRefMES14>token</AppRefMES14>\n      \n      <PriMES15>t</PriMES15>\n      \n      <AckReqMES16>1</AckReqMES16>\n      \n      <ComAgrIdMES17>token</ComAgrIdMES17>\n      \n      <TesIndMES18>1</TesIndMES18>\n      <MesIdeMES19>token</MesIdeMES19>\n      <MesTypMES20>token</MesTypMES20>\n      \n      <ComAccRefMES21>token</ComAccRefMES21>\n      \n      <MesSeqNumMES22>11</MesSeqNumMES22>\n      \n      <FirAndLasTraMES23>t</FirAndLasTraMES23>\n      <HEAHEA>\n        <DocNumHEA5>token</DocNumHEA5>\n        \n        <IdeOfMeaOfTraAtDHEA78>token</IdeOfMeaOfTraAtDHEA78>\n        \n        <IdeOfMeaOfTraAtDHEA78LNG>to</IdeOfMeaOfTraAtDHEA78LNG>\n        \n        <NatOfMeaOfTraAtDHEA80>to</NatOfMeaOfTraAtDHEA80>\n        <TotNumOfIteHEA305>11</TotNumOfIteHEA305>\n        \n        <TotNumOfPacHEA306>11</TotNumOfPacHEA306>\n        <TotGroMasHEA307>1.0</TotGroMasHEA307>\n      </HEAHEA>\n      <TRADESTRD>\n        \n        <NamTRD7>token</NamTRD7>\n        \n        <StrAndNumTRD22>token</StrAndNumTRD22>\n        \n        <PosCodTRD23>token</PosCodTRD23>\n        \n        <CitTRD24>token</CitTRD24>\n        \n        <CouTRD25>to</CouTRD25>\n        \n        <NADLNGRD>to</NADLNGRD>\n        \n        <TINTRD59>token</TINTRD59>\n      </TRADESTRD>\n      <CUSOFFPREOFFRES>\n        <RefNumRES1>tokenval</RefNumRES1>\n      </CUSOFFPREOFFRES>\n      <UNLREMREM>\n        \n        <StaOfTheSeaOKREM19>1</StaOfTheSeaOKREM19>\n        \n        <UnlRemREM53>token</UnlRemREM53>\n        \n        <UnlRemREM53LNG>to</UnlRemREM53LNG>\n        <ConREM65>1</ConREM65>\n        <UnlComREM66>1</UnlComREM66>\n        <UnlDatREM67>11010110</UnlDatREM67>\n      </UNLREMREM>\n      \n      <RESOFCON534>\n        \n        <DesTOC2>token</DesTOC2>\n        \n        <DesTOC2LNG>to</DesTOC2LNG>\n        <ConInd424>to</ConInd424>\n        \n        <PoiToTheAttTOC5>token</PoiToTheAttTOC5>\n        \n        <CorValTOC4>token</CorValTOC4>\n      </RESOFCON534>\n      \n      <SEAINFSLI>\n        <SeaNumSLI2>tval</SeaNumSLI2>\n        \n        <SEAIDSID>\n          <SeaIdeSID1>token</SeaIdeSID1>\n          \n          <SeaIdeSID1LNG>to</SeaIdeSID1LNG>\n        </SEAIDSID>\n      </SEAINFSLI>\n      \n      <GOOITEGDS>\n        <IteNumGDS7>1</IteNumGDS7>\n        \n        <ComCodTarCodGDS10>token</ComCodTarCodGDS10>\n        \n        <GooDesGDS23>token</GooDesGDS23>\n        \n        <GooDesGDS23LNG>to</GooDesGDS23LNG>\n        \n        <GroMasGDS46>1.0</GroMasGDS46>\n        \n        <NetMasGDS48>1.0</NetMasGDS48>\n        \n        <PRODOCDC2>\n          <DocTypDC21>tval</DocTypDC21>\n          \n          <DocRefDC23>token</DocRefDC23>\n          \n          <DocRefDCLNG>to</DocRefDCLNG>\n          \n          <ComOfInfDC25>token</ComOfInfDC25>\n          \n          <ComOfInfDC25LNG>to</ComOfInfDC25LNG>\n        </PRODOCDC2>\n        \n        <RESOFCONROC>\n          \n          <DesROC2>token</DesROC2>\n          \n          <DesROC2LNG>to</DesROC2LNG>\n          <ConIndROC1>to</ConIndROC1>\n          \n          <PoiToTheAttROC51>token</PoiToTheAttROC51>\n        </RESOFCONROC>\n        \n        <CONNR2>\n          <ConNumNR21>token</ConNumNR21>\n        </CONNR2>\n        \n        <PACGS2>\n          \n          <MarNumOfPacGS21>token</MarNumOfPacGS21>\n          \n          <MarNumOfPacGS21LNG>to</MarNumOfPacGS21LNG>\n          <KinOfPacGS23>val</KinOfPacGS23>\n          \n          <NumOfPacGS24>token</NumOfPacGS24>\n          \n          <NumOfPieGS25>token</NumOfPieGS25>\n        </PACGS2>\n        \n        <SGICODSD2>\n          \n          <SenGooCodSD22>1</SenGooCodSD22>\n          \n          <SenQuaSD23>1.0</SenQuaSD23>\n        </SGICODSD2>\n      </GOOITEGDS>\n    </CC044A>"
          |}""".stripMargin)

      status(result) mustBe ACCEPTED
      contentAsString(result) mustEqual expectedJson.toString()
    }

    "must return BadRequest when xml includes MesSenMES3" in {
      val request = fakeRequestMessages(method = "POST", uri = routes.ArrivalMessagesController.sendMessageDownstream("123").url, body = CC044AwithMesSenMES3)
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }

    "must return InternalServerError when unsuccessful" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))

      val request = fakeRequestMessages(method = "POST", uri = routes.ArrivalMessagesController.sendMessageDownstream("123").url, body = CC044A)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when no Location in downstream response header" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Headers.create().toMap) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.ArrivalMessagesController.sendMessageDownstream("123").url, body = CC044A)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when invalid Location value in downstream response header" ignore {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/<>"))) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.ArrivalMessagesController.sendMessageDownstream("123").url, body = CC044A)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must escape arrival ID in Location response header" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/123-@+*~-31@"))) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.ArrivalMessagesController.sendMessageDownstream("123").url, body = CC044A)
      val result = route(app, request).value

      val expectedJson = Json.parse(
        """
          |{
          |  "_links": [
          |    {
          |      "self": {
          |        "href": "/customs/transits/movements/arrivals/123/messages/123-@+*~-31@"
          |      }
          |    },
          |    {
          |      "arrival": {
          |        "href": "/customs/transits/movements/arrivals/123"
          |      }
          |    }
          |  ],
          |  "arrivalId": "123",
          |  "messageId": "123-@+*~-31@",
          |  "messageType": "IE044",
          |  "body": "<CC044A>\n      <SynIdeMES1>tval</SynIdeMES1>\n      <SynVerNumMES2>1</SynVerNumMES2>\n      \n      <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>\n      <MesRecMES6>111111</MesRecMES6>\n      \n      <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>\n      <DatOfPreMES9>20001001</DatOfPreMES9>\n      <TimOfPreMES10>1111</TimOfPreMES10>\n      <IntConRefMES11>111111</IntConRefMES11>\n      \n      <RecRefMES12>111111</RecRefMES12>\n      \n      <RecRefQuaMES13>to</RecRefQuaMES13>\n      \n      <AppRefMES14>token</AppRefMES14>\n      \n      <PriMES15>t</PriMES15>\n      \n      <AckReqMES16>1</AckReqMES16>\n      \n      <ComAgrIdMES17>token</ComAgrIdMES17>\n      \n      <TesIndMES18>1</TesIndMES18>\n      <MesIdeMES19>token</MesIdeMES19>\n      <MesTypMES20>token</MesTypMES20>\n      \n      <ComAccRefMES21>token</ComAccRefMES21>\n      \n      <MesSeqNumMES22>11</MesSeqNumMES22>\n      \n      <FirAndLasTraMES23>t</FirAndLasTraMES23>\n      <HEAHEA>\n        <DocNumHEA5>token</DocNumHEA5>\n        \n        <IdeOfMeaOfTraAtDHEA78>token</IdeOfMeaOfTraAtDHEA78>\n        \n        <IdeOfMeaOfTraAtDHEA78LNG>to</IdeOfMeaOfTraAtDHEA78LNG>\n        \n        <NatOfMeaOfTraAtDHEA80>to</NatOfMeaOfTraAtDHEA80>\n        <TotNumOfIteHEA305>11</TotNumOfIteHEA305>\n        \n        <TotNumOfPacHEA306>11</TotNumOfPacHEA306>\n        <TotGroMasHEA307>1.0</TotGroMasHEA307>\n      </HEAHEA>\n      <TRADESTRD>\n        \n        <NamTRD7>token</NamTRD7>\n        \n        <StrAndNumTRD22>token</StrAndNumTRD22>\n        \n        <PosCodTRD23>token</PosCodTRD23>\n        \n        <CitTRD24>token</CitTRD24>\n        \n        <CouTRD25>to</CouTRD25>\n        \n        <NADLNGRD>to</NADLNGRD>\n        \n        <TINTRD59>token</TINTRD59>\n      </TRADESTRD>\n      <CUSOFFPREOFFRES>\n        <RefNumRES1>tokenval</RefNumRES1>\n      </CUSOFFPREOFFRES>\n      <UNLREMREM>\n        \n        <StaOfTheSeaOKREM19>1</StaOfTheSeaOKREM19>\n        \n        <UnlRemREM53>token</UnlRemREM53>\n        \n        <UnlRemREM53LNG>to</UnlRemREM53LNG>\n        <ConREM65>1</ConREM65>\n        <UnlComREM66>1</UnlComREM66>\n        <UnlDatREM67>11010110</UnlDatREM67>\n      </UNLREMREM>\n      \n      <RESOFCON534>\n        \n        <DesTOC2>token</DesTOC2>\n        \n        <DesTOC2LNG>to</DesTOC2LNG>\n        <ConInd424>to</ConInd424>\n        \n        <PoiToTheAttTOC5>token</PoiToTheAttTOC5>\n        \n        <CorValTOC4>token</CorValTOC4>\n      </RESOFCON534>\n      \n      <SEAINFSLI>\n        <SeaNumSLI2>tval</SeaNumSLI2>\n        \n        <SEAIDSID>\n          <SeaIdeSID1>token</SeaIdeSID1>\n          \n          <SeaIdeSID1LNG>to</SeaIdeSID1LNG>\n        </SEAIDSID>\n      </SEAINFSLI>\n      \n      <GOOITEGDS>\n        <IteNumGDS7>1</IteNumGDS7>\n        \n        <ComCodTarCodGDS10>token</ComCodTarCodGDS10>\n        \n        <GooDesGDS23>token</GooDesGDS23>\n        \n        <GooDesGDS23LNG>to</GooDesGDS23LNG>\n        \n        <GroMasGDS46>1.0</GroMasGDS46>\n        \n        <NetMasGDS48>1.0</NetMasGDS48>\n        \n        <PRODOCDC2>\n          <DocTypDC21>tval</DocTypDC21>\n          \n          <DocRefDC23>token</DocRefDC23>\n          \n          <DocRefDCLNG>to</DocRefDCLNG>\n          \n          <ComOfInfDC25>token</ComOfInfDC25>\n          \n          <ComOfInfDC25LNG>to</ComOfInfDC25LNG>\n        </PRODOCDC2>\n        \n        <RESOFCONROC>\n          \n          <DesROC2>token</DesROC2>\n          \n          <DesROC2LNG>to</DesROC2LNG>\n          <ConIndROC1>to</ConIndROC1>\n          \n          <PoiToTheAttROC51>token</PoiToTheAttROC51>\n        </RESOFCONROC>\n        \n        <CONNR2>\n          <ConNumNR21>token</ConNumNR21>\n        </CONNR2>\n        \n        <PACGS2>\n          \n          <MarNumOfPacGS21>token</MarNumOfPacGS21>\n          \n          <MarNumOfPacGS21LNG>to</MarNumOfPacGS21LNG>\n          <KinOfPacGS23>val</KinOfPacGS23>\n          \n          <NumOfPacGS24>token</NumOfPacGS24>\n          \n          <NumOfPieGS25>token</NumOfPieGS25>\n        </PACGS2>\n        \n        <SGICODSD2>\n          \n          <SenGooCodSD22>1</SenGooCodSD22>\n          \n          <SenQuaSD23>1.0</SenQuaSD23>\n        </SGICODSD2>\n      </GOOITEGDS>\n    </CC044A>"
          |}
          |""".stripMargin)

      status(result) mustBe ACCEPTED
      contentAsString(result) mustEqual expectedJson.toString()
    }

    "must exclude query string if present in downstream Location header" in {
      when(mockMessageConnector.post(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful( HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transit-movements-trader-at-destination/movements/arrivals/123/messages/123?status=success"))) ))

      val request = fakeRequestMessages(method = "POST", uri = routes.ArrivalMessagesController.sendMessageDownstream("123").url, body = CC044A)
      val result = route(app, request).value

      val expectedJson = Json.parse(
        """
          |{
          |  "_links": [
          |    {
          |      "self": {
          |        "href": "/customs/transits/movements/arrivals/123/messages/123"
          |      }
          |    },
          |    {
          |      "arrival": {
          |        "href": "/customs/transits/movements/arrivals/123"
          |      }
          |    }
          |  ],
          |  "arrivalId": "123",
          |  "messageId": "123",
          |  "messageType": "IE044",
          |  "body": "<CC044A>\n      <SynIdeMES1>tval</SynIdeMES1>\n      <SynVerNumMES2>1</SynVerNumMES2>\n      \n      <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>\n      <MesRecMES6>111111</MesRecMES6>\n      \n      <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>\n      <DatOfPreMES9>20001001</DatOfPreMES9>\n      <TimOfPreMES10>1111</TimOfPreMES10>\n      <IntConRefMES11>111111</IntConRefMES11>\n      \n      <RecRefMES12>111111</RecRefMES12>\n      \n      <RecRefQuaMES13>to</RecRefQuaMES13>\n      \n      <AppRefMES14>token</AppRefMES14>\n      \n      <PriMES15>t</PriMES15>\n      \n      <AckReqMES16>1</AckReqMES16>\n      \n      <ComAgrIdMES17>token</ComAgrIdMES17>\n      \n      <TesIndMES18>1</TesIndMES18>\n      <MesIdeMES19>token</MesIdeMES19>\n      <MesTypMES20>token</MesTypMES20>\n      \n      <ComAccRefMES21>token</ComAccRefMES21>\n      \n      <MesSeqNumMES22>11</MesSeqNumMES22>\n      \n      <FirAndLasTraMES23>t</FirAndLasTraMES23>\n      <HEAHEA>\n        <DocNumHEA5>token</DocNumHEA5>\n        \n        <IdeOfMeaOfTraAtDHEA78>token</IdeOfMeaOfTraAtDHEA78>\n        \n        <IdeOfMeaOfTraAtDHEA78LNG>to</IdeOfMeaOfTraAtDHEA78LNG>\n        \n        <NatOfMeaOfTraAtDHEA80>to</NatOfMeaOfTraAtDHEA80>\n        <TotNumOfIteHEA305>11</TotNumOfIteHEA305>\n        \n        <TotNumOfPacHEA306>11</TotNumOfPacHEA306>\n        <TotGroMasHEA307>1.0</TotGroMasHEA307>\n      </HEAHEA>\n      <TRADESTRD>\n        \n        <NamTRD7>token</NamTRD7>\n        \n        <StrAndNumTRD22>token</StrAndNumTRD22>\n        \n        <PosCodTRD23>token</PosCodTRD23>\n        \n        <CitTRD24>token</CitTRD24>\n        \n        <CouTRD25>to</CouTRD25>\n        \n        <NADLNGRD>to</NADLNGRD>\n        \n        <TINTRD59>token</TINTRD59>\n      </TRADESTRD>\n      <CUSOFFPREOFFRES>\n        <RefNumRES1>tokenval</RefNumRES1>\n      </CUSOFFPREOFFRES>\n      <UNLREMREM>\n        \n        <StaOfTheSeaOKREM19>1</StaOfTheSeaOKREM19>\n        \n        <UnlRemREM53>token</UnlRemREM53>\n        \n        <UnlRemREM53LNG>to</UnlRemREM53LNG>\n        <ConREM65>1</ConREM65>\n        <UnlComREM66>1</UnlComREM66>\n        <UnlDatREM67>11010110</UnlDatREM67>\n      </UNLREMREM>\n      \n      <RESOFCON534>\n        \n        <DesTOC2>token</DesTOC2>\n        \n        <DesTOC2LNG>to</DesTOC2LNG>\n        <ConInd424>to</ConInd424>\n        \n        <PoiToTheAttTOC5>token</PoiToTheAttTOC5>\n        \n        <CorValTOC4>token</CorValTOC4>\n      </RESOFCON534>\n      \n      <SEAINFSLI>\n        <SeaNumSLI2>tval</SeaNumSLI2>\n        \n        <SEAIDSID>\n          <SeaIdeSID1>token</SeaIdeSID1>\n          \n          <SeaIdeSID1LNG>to</SeaIdeSID1LNG>\n        </SEAIDSID>\n      </SEAINFSLI>\n      \n      <GOOITEGDS>\n        <IteNumGDS7>1</IteNumGDS7>\n        \n        <ComCodTarCodGDS10>token</ComCodTarCodGDS10>\n        \n        <GooDesGDS23>token</GooDesGDS23>\n        \n        <GooDesGDS23LNG>to</GooDesGDS23LNG>\n        \n        <GroMasGDS46>1.0</GroMasGDS46>\n        \n        <NetMasGDS48>1.0</NetMasGDS48>\n        \n        <PRODOCDC2>\n          <DocTypDC21>tval</DocTypDC21>\n          \n          <DocRefDC23>token</DocRefDC23>\n          \n          <DocRefDCLNG>to</DocRefDCLNG>\n          \n          <ComOfInfDC25>token</ComOfInfDC25>\n          \n          <ComOfInfDC25LNG>to</ComOfInfDC25LNG>\n        </PRODOCDC2>\n        \n        <RESOFCONROC>\n          \n          <DesROC2>token</DesROC2>\n          \n          <DesROC2LNG>to</DesROC2LNG>\n          <ConIndROC1>to</ConIndROC1>\n          \n          <PoiToTheAttROC51>token</PoiToTheAttROC51>\n        </RESOFCONROC>\n        \n        <CONNR2>\n          <ConNumNR21>token</ConNumNR21>\n        </CONNR2>\n        \n        <PACGS2>\n          \n          <MarNumOfPacGS21>token</MarNumOfPacGS21>\n          \n          <MarNumOfPacGS21LNG>to</MarNumOfPacGS21LNG>\n          <KinOfPacGS23>val</KinOfPacGS23>\n          \n          <NumOfPacGS24>token</NumOfPacGS24>\n          \n          <NumOfPieGS25>token</NumOfPieGS25>\n        </PACGS2>\n        \n        <SGICODSD2>\n          \n          <SenGooCodSD22>1</SenGooCodSD22>\n          \n          <SenQuaSD23>1.0</SenQuaSD23>\n        </SGICODSD2>\n      </GOOITEGDS>\n    </CC044A>"
          |}""".stripMargin)

      status(result) mustBe ACCEPTED
      contentAsString(result) mustEqual expectedJson.toString()
    }

    "must return UnsupportedMediaType when Content-Type is JSON" in {
      val request = FakeRequest(method = "POST", uri = routes.ArrivalMessagesController.sendMessageDownstream("123").url, headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json")), body = AnyContentAsEmpty)

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when no Content-Type specified" in {
      val request = fakeRequestMessages(method = "POST", headers = FakeHeaders(), uri = routes.ArrivalMessagesController.sendMessageDownstream("123").url, body = ByteString("body"))

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when empty XML payload is sent" in {
      val request = fakeRequestMessages(method = "POST", headers = FakeHeaders(), uri = routes.ArrivalMessagesController.sendMessageDownstream("123").url, body = AnyContentAsEmpty)

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return BadRequest when invalid XML payload is sent" in {
      val request = fakeRequestMessages(method = "POST", uri = routes.ArrivalMessagesController.sendMessageDownstream("123").url, body = InvalidCC044A)

      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }
  }

  "GET /movements/arrivals/:arrivalId/messages" - {
    "return 200 with body of arrival and messages" in {
      when(mockMessageConnector.getMessages(any())(any(), any(), any()))
        .thenReturn(Future.successful(Right(sourceArrival)))

      val request = FakeRequest("GET", routes.ArrivalMessagesController.getArrivalMessages("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)

      val result = route(app, request).value

      contentAsString(result) mustEqual expectedArrivalResult.toString()
      status(result) mustBe OK
    }

    "return 404 if downstream returns 404" in {
      when(mockMessageConnector.getMessages(any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(404, ""))))

      val request = FakeRequest("GET", routes.ArrivalMessagesController.getArrivalMessages("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe NOT_FOUND
    }

    "return 500 for other downstream errors" in {
      when(mockMessageConnector.getMessages(any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(INTERNAL_SERVER_ERROR, json, Headers.create().toMap) )))

      val request = FakeRequest("GET", routes.ArrivalMessagesController.getArrivalMessages("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "return 500 if downstream provides an unsafe message header" ignore {
      when(mockMessageConnector.getMessages(any())(any(), any(), any()))
        .thenReturn(Future.successful(Right(sourceArrival.copy(messages = Seq(sourceMovement.copy(location = "/transit-movements-trader-at-destination/movements/arrivals/<>"))))))

      val request = FakeRequest("GET", routes.ArrivalMessagesController.getArrivalMessages("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }
}
