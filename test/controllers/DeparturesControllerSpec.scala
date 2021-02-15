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

import audit.AuditService
import connectors.DeparturesConnector
import controllers.actions.{AuthAction, FakeAuthAction}
import data.TestXml
import models.domain.{Departure, Departures}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
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
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers.{headers, _}
import play.api.test.{FakeHeaders, FakeRequest}
import services.EnsureGuaranteeService
import uk.gov.hmrc.http.HttpResponse
import utils.CallOps._

import scala.concurrent.Future

class DeparturesControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach with TestXml {
  private val mockDepartureConnector: DeparturesConnector = mock[DeparturesConnector]
  private val mockGuaranteeService: EnsureGuaranteeService = mock[EnsureGuaranteeService]
  private val mockAuditService: AuditService = mock[AuditService]

  when(mockGuaranteeService.ensureGuarantee(any())).thenReturn(Right(CC015B))

  override lazy val app = GuiceApplicationBuilder()
    .overrides(bind[AuthAction].to[FakeAuthAction])
    .overrides(bind[DeparturesConnector].toInstance(mockDepartureConnector))
    .overrides(bind[EnsureGuaranteeService].toInstance(mockGuaranteeService))
    .overrides(bind[AuditService].toInstance(mockAuditService))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockDepartureConnector)
    reset(mockAuditService)
  }

  val sourceDeparture = Departure(123, routes.DeparturesController.getDeparture("123").urlWithContext, routes.DepartureMessagesController.getDepartureMessages("123").urlWithContext, Some("MRN"), "status", LocalDateTime.of(2020, 2, 2, 2, 2, 2), LocalDateTime.of(2020, 2, 2, 2, 2, 2))

  val expectedDeparture = Json.parse(
    """
      |{
      |  "_links": {
      |    "self": {
      |      "href": "/customs/transits/movements/departures"
      |    }
      |  },
      |  "_embedded": {
      |    "departures": [
      |      {
      |        "id": "123",
      |        "created": "2020-02-02T02:02:02",
      |        "updated": "2020-02-02T02:02:02",
      |        "movementReferenceNumber": "MRN",
      |        "status": "status",
      |        "_links": {
      |          "self": {
      |            "href": "/customs/transits/movements/departures/123"
      |          },
      |          "messages": {
      |            "href": "/customs/transits/movements/departures/123/messages"
      |          }
      |        }
      |      },
      |      {
      |        "id": "123",
      |        "created": "2020-02-02T02:02:02",
      |        "updated": "2020-02-02T02:02:02",
      |        "movementReferenceNumber": "MRN",
      |        "status": "status",
      |        "_links": {
      |          "self": {
      |            "href": "/customs/transits/movements/departures/123"
      |          },
      |          "messages": {
      |            "href": "/customs/transits/movements/departures/123/messages"
      |          }
      |        }
      |      },
      |      {
      |        "id": "123",
      |        "created": "2020-02-02T02:02:02",
      |        "updated": "2020-02-02T02:02:02",
      |        "movementReferenceNumber": "MRN",
      |        "status": "status",
      |        "_links": {
      |          "self": {
      |            "href": "/customs/transits/movements/departures/123"
      |          },
      |          "messages": {
      |            "href": "/customs/transits/movements/departures/123/messages"
      |          }
      |        }
      |      }
      |    ]
      |  }
      |}""".stripMargin)

  val expectedDepartureResult = Json.parse(
    """
      |{
      |  "id": "123",
      |  "created": "2020-02-02T02:02:02",
      |  "updated": "2020-02-02T02:02:02",
      |  "movementReferenceNumber": "MRN",
      |  "status": "status",
      |  "_links": {
      |    "self": {
      |      "href": "/customs/transits/movements/departures/123"
      |    },
      |    "messages": {
      |      "href": "/customs/transits/movements/departures/123/messages"
      |    }
      |  }
      |}""".stripMargin)

  def fakeRequestDepartures[A](method: String, headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), uri: String = routes.DeparturesController.submitDeclaration().url, body: A) =
    FakeRequest(method = method, uri = uri, headers, body = body)

  "POST /movements/departures" - {
    "must return Accepted when successful" - {

      "and create an audit event if the guarantee was changed" in {
        when(mockDepartureConnector.post(any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123"))) ))

        val request = fakeRequestDepartures(method = "POST", body = CC015BRequiringDefaultGuarantee)
        val result = route(app, request).value

        val expectedJson = Json.parse(
          """
            |{
            |  "_links": {
            |    "self": {
            |      "href": "/customs/transits/movements/departures/123"
            |    }
            |  },
            |  "departureId": "123",
            |  "messageType": "IE015",
            |  "body": "<CC015B>\n    <SynIdeMES1>UNOC</SynIdeMES1>\n    <SynVerNumMES2>3</SynVerNumMES2>\n    <MesRecMES6>NCTS</MesRecMES6>\n    <DatOfPreMES9>20201217</DatOfPreMES9>\n    <TimOfPreMES10>1340</TimOfPreMES10>\n    <IntConRefMES11>17712576475433</IntConRefMES11>\n    <AppRefMES14>NCTS</AppRefMES14>\n    <MesIdeMES19>1</MesIdeMES19>\n    <MesTypMES20>GB015B</MesTypMES20>\n    <HEAHEA>\n      <RefNumHEA4>GUATEST1201217134032</RefNumHEA4>\n      <TypOfDecHEA24>T1</TypOfDecHEA24>\n      <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>\n      <AutLocOfGooCodHEA41>954131533-GB60DEP</AutLocOfGooCodHEA41>\n      <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>\n      <IdeOfMeaOfTraAtDHEA78>NC15 REG</IdeOfMeaOfTraAtDHEA78>\n      <NatOfMeaOfTraAtDHEA80>GB</NatOfMeaOfTraAtDHEA80>\n      <ConIndHEA96>0</ConIndHEA96>\n      <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>\n      <TotNumOfIteHEA305>1</TotNumOfIteHEA305>\n      <TotNumOfPacHEA306>10</TotNumOfPacHEA306>\n      <TotGroMasHEA307>1000</TotGroMasHEA307>\n      <DecDatHEA383>20201217</DecDatHEA383>\n      <DecPlaHEA394>Dover</DecPlaHEA394>\n    </HEAHEA>\n    <TRAPRIPC1>\n      <NamPC17>NCTS UK TEST LAB HMCE</NamPC17>\n      <StrAndNumPC122>11TH FLOOR, ALEX HOUSE, VICTORIA AV</StrAndNumPC122>\n      <PosCodPC123>SS99 1AA</PosCodPC123>\n      <CitPC124>SOUTHEND-ON-SEA, ESSEX</CitPC124>\n      <CouPC125>GB</CouPC125>\n      <TINPC159>GB954131533000</TINPC159>\n    </TRAPRIPC1>\n    <TRACONCO1>\n      <NamCO17>NCTS UK TEST LAB HMCE</NamCO17>\n      <StrAndNumCO122>11TH FLOOR, ALEX HOUSE, VICTORIA AV</StrAndNumCO122>\n      <PosCodCO123>SS99 1AA</PosCodCO123>\n      <CitCO124>SOUTHEND-ON-SEA, ESSEX</CitCO124>\n      <CouCO125>GB</CouCO125>\n      <TINCO159>GB954131533000</TINCO159>\n    </TRACONCO1>\n    <TRACONCE1>\n      <NamCE17>NCTS UK TEST LAB HMCE</NamCE17>\n      <StrAndNumCE122>ITALIAN OFFICE</StrAndNumCE122>\n      <PosCodCE123>IT99 1IT</PosCodCE123>\n      <CitCE124>MILAN</CitCE124>\n      <CouCE125>IT</CouCE125>\n      <TINCE159>IT11ITALIANC11</TINCE159>\n    </TRACONCE1>\n    <CUSOFFDEPEPT>\n      <RefNumEPT1>GB000060</RefNumEPT1>\n    </CUSOFFDEPEPT>\n    <CUSOFFTRARNS>\n      <RefNumRNS1>FR001260</RefNumRNS1>\n      <ArrTimTRACUS085>202012191340</ArrTimTRACUS085>\n    </CUSOFFTRARNS>\n    <CUSOFFDESEST>\n      <RefNumEST1>IT018100</RefNumEST1>\n    </CUSOFFDESEST>\n    <CONRESERS>\n      <ConResCodERS16>A3</ConResCodERS16>\n      <DatLimERS69>20201225</DatLimERS69>\n    </CONRESERS>\n    <SEAINFSLI>\n      <SeaNumSLI2>1</SeaNumSLI2>\n      <SEAIDSID>\n        <SeaIdeSID1>NCTS001</SeaIdeSID1>\n      </SEAIDSID>\n    </SEAINFSLI>\n    <GUAGUA>\n      <GuaTypGUA1>0</GuaTypGUA1>\n      <GUAREFREF>\n        <GuaRefNumGRNREF1>20GB0000010000H72</GuaRefNumGRNREF1>\n        <AccCodREF6>AC01</AccCodREF6>\n      </GUAREFREF>\n    </GUAGUA>\n    <GOOITEGDS>\n      <IteNumGDS7>1</IteNumGDS7>\n      <GooDesGDS23>Wheat</GooDesGDS23>\n      <GooDesGDS23LNG>EN</GooDesGDS23LNG>\n      <GroMasGDS46>1000</GroMasGDS46>\n      <NetMasGDS48>950</NetMasGDS48>\n      <SPEMENMT2>\n        <AddInfMT21>20GB0000010000H72</AddInfMT21>\n        <AddInfCodMT23>CAL</AddInfCodMT23>\n      </SPEMENMT2>\n      <PACGS2>\n        <MarNumOfPacGS21>AB234</MarNumOfPacGS21>\n        <KinOfPacGS23>BX</KinOfPacGS23>\n        <NumOfPacGS24>10</NumOfPacGS24>\n      </PACGS2>\n    </GOOITEGDS>\n  </CC015B>"
            |}""".stripMargin)

        status(result) mustBe ACCEPTED
        contentAsString(result) mustEqual expectedJson.toString()
        headers(result) must contain (LOCATION -> routes.DeparturesController.getDeparture("123").urlWithContext)

        verify(mockAuditService, times(1)).auditEvent(any(), any())(any())
      }

      "and not create an audit event when the guarantee was not changed" in {
        when(mockDepartureConnector.post(any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123"))) ))

        val request = fakeRequestDepartures(method = "POST", body = CC015B)
        val result = route(app, request).value

        val expectedJson = Json.parse(
          """
            |{
            |  "_links": {
            |    "self": {
            |      "href": "/customs/transits/movements/departures/123"
            |    }
            |  },
            |  "departureId": "123",
            |  "messageType": "IE015",
            |  "body": "<CC015B>\n    <SynIdeMES1>UNOC</SynIdeMES1>\n    <SynVerNumMES2>3</SynVerNumMES2>\n    <MesRecMES6>NCTS</MesRecMES6>\n    <DatOfPreMES9>20190912</DatOfPreMES9>\n    <TimOfPreMES10>1222</TimOfPreMES10>\n    <IntConRefMES11>WE190912102530</IntConRefMES11>\n    <AppRefMES14>NCTS</AppRefMES14>\n    <TesIndMES18>0</TesIndMES18>\n    <MesIdeMES19>1</MesIdeMES19>\n    <MesTypMES20>GB015B</MesTypMES20>\n    <HEAHEA>\n      <RefNumHEA4>01CTC201909121215</RefNumHEA4>\n      <TypOfDecHEA24>T2</TypOfDecHEA24>\n      <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>\n      <AgrLocOfGooCodHEA38>default</AgrLocOfGooCodHEA38>\n      <AgrLocOfGooHEA39>default</AgrLocOfGooHEA39>\n      <AgrLocOfGooHEA39LNG>EN</AgrLocOfGooHEA39LNG>\n      <AutLocOfGooCodHEA41>default</AutLocOfGooCodHEA41>\n      <PlaOfLoaCodHEA46>DOVER</PlaOfLoaCodHEA46>\n      <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>\n      <CusSubPlaHEA66>default</CusSubPlaHEA66>\n      <InlTraModHEA75>20</InlTraModHEA75>\n      <IdeOfMeaOfTraAtDHEA78>EU_EXIT</IdeOfMeaOfTraAtDHEA78>\n      <IdeOfMeaOfTraAtDHEA78LNG>EN</IdeOfMeaOfTraAtDHEA78LNG>\n      <IdeOfMeaOfTraCroHEA85>EU_EXIT</IdeOfMeaOfTraCroHEA85>\n      <IdeOfMeaOfTraCroHEA85LNG>EN</IdeOfMeaOfTraCroHEA85LNG>\n      <ConIndHEA96>0</ConIndHEA96>\n      <DiaLanIndAtDepHEA254>EN</DiaLanIndAtDepHEA254>\n      <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>\n      <TotNumOfIteHEA305>1</TotNumOfIteHEA305>\n      <TotNumOfPacHEA306>1</TotNumOfPacHEA306>\n      <TotGroMasHEA307>1000</TotGroMasHEA307>\n      <DecDatHEA383>20190912</DecDatHEA383>\n      <DecPlaHEA394>DOVER</DecPlaHEA394>\n      <DecPlaHEA394LNG>EN</DecPlaHEA394LNG>\n    </HEAHEA>\n    <TRAPRIPC1>\n      <NamPC17>CITY WATCH SHIPPING</NamPC17>\n      <StrAndNumPC122>125 Psuedopolis Yard</StrAndNumPC122>\n      <PosCodPC123>SS99 1AA</PosCodPC123>\n      <CitPC124>Ank-Morpork</CitPC124>\n      <CouPC125>GB</CouPC125>\n      <NADLNGPC>EN</NADLNGPC>\n      <TINPC159>GB652420267000</TINPC159>\n    </TRAPRIPC1>\n    <TRACONCO1>\n      <NamCO17>QUIRM ENGINEERING</NamCO17>\n      <StrAndNumCO122>125 Psuedopolis Yard</StrAndNumCO122>\n      <PosCodCO123>SS99 1AA</PosCodCO123>\n      <CitCO124>Ank-Morpork</CitCO124>\n      <CouCO125>GB</CouCO125>\n      <TINCO159>GB602070107000</TINCO159>\n    </TRACONCO1>\n    <TRACONCE1>\n      <NamCE17>DROFL POTTERY</NamCE17>\n      <StrAndNumCE122>125 Psuedopolis Yard</StrAndNumCE122>\n      <PosCodCE123>SS99 1AA</PosCodCE123>\n      <CitCE124>Ank-Morpork</CitCE124>\n      <CouCE125>GB</CouCE125>\n      <NADLNGCE>EN</NADLNGCE>\n      <TINCE159>GB658120050000</TINCE159>\n    </TRACONCE1>\n    <CUSOFFDEPEPT>\n      <RefNumEPT1>GB000060</RefNumEPT1>\n    </CUSOFFDEPEPT>\n    <CUSOFFTRARNS>\n      <RefNumRNS1>FR001260</RefNumRNS1>\n      <ArrTimTRACUS085>201909160100</ArrTimTRACUS085>\n    </CUSOFFTRARNS>\n    <CUSOFFDESEST>\n      <RefNumEST1>IT021100</RefNumEST1>\n    </CUSOFFDESEST>\n    <SEAINFSLI>\n      <SeaNumSLI2>1</SeaNumSLI2>\n      <SEAIDSID>\n        <SeaIdeSID1>Seal001</SeaIdeSID1>\n        <SeaIdeSID1LNG>EN</SeaIdeSID1LNG>\n      </SEAIDSID>\n    </SEAINFSLI>\n    <GUAGUA>\n      <GuaTypGUA1>3</GuaTypGUA1>\n      <GUAREFREF>\n        <GuaRefNumGRNREF1>default</GuaRefNumGRNREF1>\n        <OthGuaRefREF4>EU_EXIT</OthGuaRefREF4>\n        <AccCodREF6>test</AccCodREF6>\n      </GUAREFREF>\n    </GUAGUA>\n    <GOOITEGDS>\n      <IteNumGDS7>1</IteNumGDS7>\n      <ComCodTarCodGDS10>default</ComCodTarCodGDS10>\n      <DecTypGDS15>default</DecTypGDS15>\n      <GooDesGDS23>Flowers</GooDesGDS23>\n      <GooDesGDS23LNG>EN</GooDesGDS23LNG>\n      <GroMasGDS46>1000</GroMasGDS46>\n      <NetMasGDS48>999</NetMasGDS48>\n      <CouOfDesGDS59>ex</CouOfDesGDS59>\n      <PREADMREFAR2>\n        <PreDocTypAR21>T2</PreDocTypAR21>\n        <PreDocRefAR26>EU_EXIT-T2</PreDocRefAR26>\n        <PreDocRefLNG>EN</PreDocRefLNG>\n        <ComOfInfAR29>default</ComOfInfAR29>\n        <ComOfInfAR29LNG>EN</ComOfInfAR29LNG>\n      </PREADMREFAR2>\n      <PRODOCDC2>\n        <DocTypDC21>720</DocTypDC21>\n        <DocRefDC23>EU_EXIT</DocRefDC23>\n        <DocRefDCLNG>EN</DocRefDCLNG>\n        <ComOfInfDC25>default</ComOfInfDC25>\n        <ComOfInfDC25LNG>EN</ComOfInfDC25LNG>\n      </PRODOCDC2>\n      <PACGS2>\n        <MarNumOfPacGS21>Bloomingales</MarNumOfPacGS21>\n        <MarNumOfPacGS21LNG>EN</MarNumOfPacGS21LNG>\n        <KinOfPacGS23>BX</KinOfPacGS23>\n        <NumOfPacGS24>1</NumOfPacGS24>\n      </PACGS2>\n    </GOOITEGDS>\n  </CC015B>"
            |}""".stripMargin)

        status(result) mustBe ACCEPTED
        contentAsString(result) mustEqual expectedJson.toString()
        headers(result) must contain (LOCATION -> routes.DeparturesController.getDeparture("123").urlWithContext)

        verify(mockAuditService, times(0)).auditEvent(any(), any())(any())
      }
    }

    "must return BadRequest when xml includes MesSenMES3" in {
      val request = fakeRequestDepartures(method = "POST", body = CC015BwithMesSenMES3)
      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }

    "must return InternalServerError when unsuccessful" in {
      when(mockDepartureConnector.post(any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))

      val request = fakeRequestDepartures(method = "POST", body = CC015B)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when no location in downstream response header" in {
      when(mockDepartureConnector.post(any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "") ))

      val request = fakeRequestDepartures(method = "POST", body = CC015B)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return InternalServerError when invalid Location value in downstream response header" ignore {}

    "must escape departureId in location response header" in {
      when(mockDepartureConnector.post(any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123-@+*~-31@"))) ))

      val request = fakeRequestDepartures(method = "POST", body = CC015B)
      val result = route(app, request).value

      val expectedJson = Json.parse(
        """{
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/departures/123-@+*~-31@"
          |    }
          |  },
          |  "departureId": "123-@+*~-31@",
          |  "messageType": "IE015",
          |  "body": "<CC015B>\n    <SynIdeMES1>UNOC</SynIdeMES1>\n    <SynVerNumMES2>3</SynVerNumMES2>\n    <MesRecMES6>NCTS</MesRecMES6>\n    <DatOfPreMES9>20190912</DatOfPreMES9>\n    <TimOfPreMES10>1222</TimOfPreMES10>\n    <IntConRefMES11>WE190912102530</IntConRefMES11>\n    <AppRefMES14>NCTS</AppRefMES14>\n    <TesIndMES18>0</TesIndMES18>\n    <MesIdeMES19>1</MesIdeMES19>\n    <MesTypMES20>GB015B</MesTypMES20>\n    <HEAHEA>\n      <RefNumHEA4>01CTC201909121215</RefNumHEA4>\n      <TypOfDecHEA24>T2</TypOfDecHEA24>\n      <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>\n      <AgrLocOfGooCodHEA38>default</AgrLocOfGooCodHEA38>\n      <AgrLocOfGooHEA39>default</AgrLocOfGooHEA39>\n      <AgrLocOfGooHEA39LNG>EN</AgrLocOfGooHEA39LNG>\n      <AutLocOfGooCodHEA41>default</AutLocOfGooCodHEA41>\n      <PlaOfLoaCodHEA46>DOVER</PlaOfLoaCodHEA46>\n      <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>\n      <CusSubPlaHEA66>default</CusSubPlaHEA66>\n      <InlTraModHEA75>20</InlTraModHEA75>\n      <IdeOfMeaOfTraAtDHEA78>EU_EXIT</IdeOfMeaOfTraAtDHEA78>\n      <IdeOfMeaOfTraAtDHEA78LNG>EN</IdeOfMeaOfTraAtDHEA78LNG>\n      <IdeOfMeaOfTraCroHEA85>EU_EXIT</IdeOfMeaOfTraCroHEA85>\n      <IdeOfMeaOfTraCroHEA85LNG>EN</IdeOfMeaOfTraCroHEA85LNG>\n      <ConIndHEA96>0</ConIndHEA96>\n      <DiaLanIndAtDepHEA254>EN</DiaLanIndAtDepHEA254>\n      <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>\n      <TotNumOfIteHEA305>1</TotNumOfIteHEA305>\n      <TotNumOfPacHEA306>1</TotNumOfPacHEA306>\n      <TotGroMasHEA307>1000</TotGroMasHEA307>\n      <DecDatHEA383>20190912</DecDatHEA383>\n      <DecPlaHEA394>DOVER</DecPlaHEA394>\n      <DecPlaHEA394LNG>EN</DecPlaHEA394LNG>\n    </HEAHEA>\n    <TRAPRIPC1>\n      <NamPC17>CITY WATCH SHIPPING</NamPC17>\n      <StrAndNumPC122>125 Psuedopolis Yard</StrAndNumPC122>\n      <PosCodPC123>SS99 1AA</PosCodPC123>\n      <CitPC124>Ank-Morpork</CitPC124>\n      <CouPC125>GB</CouPC125>\n      <NADLNGPC>EN</NADLNGPC>\n      <TINPC159>GB652420267000</TINPC159>\n    </TRAPRIPC1>\n    <TRACONCO1>\n      <NamCO17>QUIRM ENGINEERING</NamCO17>\n      <StrAndNumCO122>125 Psuedopolis Yard</StrAndNumCO122>\n      <PosCodCO123>SS99 1AA</PosCodCO123>\n      <CitCO124>Ank-Morpork</CitCO124>\n      <CouCO125>GB</CouCO125>\n      <TINCO159>GB602070107000</TINCO159>\n    </TRACONCO1>\n    <TRACONCE1>\n      <NamCE17>DROFL POTTERY</NamCE17>\n      <StrAndNumCE122>125 Psuedopolis Yard</StrAndNumCE122>\n      <PosCodCE123>SS99 1AA</PosCodCE123>\n      <CitCE124>Ank-Morpork</CitCE124>\n      <CouCE125>GB</CouCE125>\n      <NADLNGCE>EN</NADLNGCE>\n      <TINCE159>GB658120050000</TINCE159>\n    </TRACONCE1>\n    <CUSOFFDEPEPT>\n      <RefNumEPT1>GB000060</RefNumEPT1>\n    </CUSOFFDEPEPT>\n    <CUSOFFTRARNS>\n      <RefNumRNS1>FR001260</RefNumRNS1>\n      <ArrTimTRACUS085>201909160100</ArrTimTRACUS085>\n    </CUSOFFTRARNS>\n    <CUSOFFDESEST>\n      <RefNumEST1>IT021100</RefNumEST1>\n    </CUSOFFDESEST>\n    <SEAINFSLI>\n      <SeaNumSLI2>1</SeaNumSLI2>\n      <SEAIDSID>\n        <SeaIdeSID1>Seal001</SeaIdeSID1>\n        <SeaIdeSID1LNG>EN</SeaIdeSID1LNG>\n      </SEAIDSID>\n    </SEAINFSLI>\n    <GUAGUA>\n      <GuaTypGUA1>3</GuaTypGUA1>\n      <GUAREFREF>\n        <GuaRefNumGRNREF1>default</GuaRefNumGRNREF1>\n        <OthGuaRefREF4>EU_EXIT</OthGuaRefREF4>\n        <AccCodREF6>test</AccCodREF6>\n      </GUAREFREF>\n    </GUAGUA>\n    <GOOITEGDS>\n      <IteNumGDS7>1</IteNumGDS7>\n      <ComCodTarCodGDS10>default</ComCodTarCodGDS10>\n      <DecTypGDS15>default</DecTypGDS15>\n      <GooDesGDS23>Flowers</GooDesGDS23>\n      <GooDesGDS23LNG>EN</GooDesGDS23LNG>\n      <GroMasGDS46>1000</GroMasGDS46>\n      <NetMasGDS48>999</NetMasGDS48>\n      <CouOfDesGDS59>ex</CouOfDesGDS59>\n      <PREADMREFAR2>\n        <PreDocTypAR21>T2</PreDocTypAR21>\n        <PreDocRefAR26>EU_EXIT-T2</PreDocRefAR26>\n        <PreDocRefLNG>EN</PreDocRefLNG>\n        <ComOfInfAR29>default</ComOfInfAR29>\n        <ComOfInfAR29LNG>EN</ComOfInfAR29LNG>\n      </PREADMREFAR2>\n      <PRODOCDC2>\n        <DocTypDC21>720</DocTypDC21>\n        <DocRefDC23>EU_EXIT</DocRefDC23>\n        <DocRefDCLNG>EN</DocRefDCLNG>\n        <ComOfInfDC25>default</ComOfInfDC25>\n        <ComOfInfDC25LNG>EN</ComOfInfDC25LNG>\n      </PRODOCDC2>\n      <PACGS2>\n        <MarNumOfPacGS21>Bloomingales</MarNumOfPacGS21>\n        <MarNumOfPacGS21LNG>EN</MarNumOfPacGS21LNG>\n        <KinOfPacGS23>BX</KinOfPacGS23>\n        <NumOfPacGS24>1</NumOfPacGS24>\n      </PACGS2>\n    </GOOITEGDS>\n  </CC015B>"
          |}""".stripMargin)

      status(result) mustBe ACCEPTED
      contentAsString(result) mustEqual expectedJson.toString()
      headers(result) must contain (LOCATION -> routes.DeparturesController.getDeparture("123-@+*~-31@").urlWithContext)
    }

    "must exclude query string if present in downstream location header" in {
      when(mockDepartureConnector.post(any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123?status=success"))) ))

      val request = fakeRequestDepartures(method = "POST", body = CC015B)
      val result = route(app, request).value

      val expectedJson = Json.parse(
        """
          |{
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/departures/123"
          |    }
          |  },
          |  "departureId": "123",
          |  "messageType": "IE015",
          |  "body": "<CC015B>\n    <SynIdeMES1>UNOC</SynIdeMES1>\n    <SynVerNumMES2>3</SynVerNumMES2>\n    <MesRecMES6>NCTS</MesRecMES6>\n    <DatOfPreMES9>20190912</DatOfPreMES9>\n    <TimOfPreMES10>1222</TimOfPreMES10>\n    <IntConRefMES11>WE190912102530</IntConRefMES11>\n    <AppRefMES14>NCTS</AppRefMES14>\n    <TesIndMES18>0</TesIndMES18>\n    <MesIdeMES19>1</MesIdeMES19>\n    <MesTypMES20>GB015B</MesTypMES20>\n    <HEAHEA>\n      <RefNumHEA4>01CTC201909121215</RefNumHEA4>\n      <TypOfDecHEA24>T2</TypOfDecHEA24>\n      <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>\n      <AgrLocOfGooCodHEA38>default</AgrLocOfGooCodHEA38>\n      <AgrLocOfGooHEA39>default</AgrLocOfGooHEA39>\n      <AgrLocOfGooHEA39LNG>EN</AgrLocOfGooHEA39LNG>\n      <AutLocOfGooCodHEA41>default</AutLocOfGooCodHEA41>\n      <PlaOfLoaCodHEA46>DOVER</PlaOfLoaCodHEA46>\n      <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>\n      <CusSubPlaHEA66>default</CusSubPlaHEA66>\n      <InlTraModHEA75>20</InlTraModHEA75>\n      <IdeOfMeaOfTraAtDHEA78>EU_EXIT</IdeOfMeaOfTraAtDHEA78>\n      <IdeOfMeaOfTraAtDHEA78LNG>EN</IdeOfMeaOfTraAtDHEA78LNG>\n      <IdeOfMeaOfTraCroHEA85>EU_EXIT</IdeOfMeaOfTraCroHEA85>\n      <IdeOfMeaOfTraCroHEA85LNG>EN</IdeOfMeaOfTraCroHEA85LNG>\n      <ConIndHEA96>0</ConIndHEA96>\n      <DiaLanIndAtDepHEA254>EN</DiaLanIndAtDepHEA254>\n      <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>\n      <TotNumOfIteHEA305>1</TotNumOfIteHEA305>\n      <TotNumOfPacHEA306>1</TotNumOfPacHEA306>\n      <TotGroMasHEA307>1000</TotGroMasHEA307>\n      <DecDatHEA383>20190912</DecDatHEA383>\n      <DecPlaHEA394>DOVER</DecPlaHEA394>\n      <DecPlaHEA394LNG>EN</DecPlaHEA394LNG>\n    </HEAHEA>\n    <TRAPRIPC1>\n      <NamPC17>CITY WATCH SHIPPING</NamPC17>\n      <StrAndNumPC122>125 Psuedopolis Yard</StrAndNumPC122>\n      <PosCodPC123>SS99 1AA</PosCodPC123>\n      <CitPC124>Ank-Morpork</CitPC124>\n      <CouPC125>GB</CouPC125>\n      <NADLNGPC>EN</NADLNGPC>\n      <TINPC159>GB652420267000</TINPC159>\n    </TRAPRIPC1>\n    <TRACONCO1>\n      <NamCO17>QUIRM ENGINEERING</NamCO17>\n      <StrAndNumCO122>125 Psuedopolis Yard</StrAndNumCO122>\n      <PosCodCO123>SS99 1AA</PosCodCO123>\n      <CitCO124>Ank-Morpork</CitCO124>\n      <CouCO125>GB</CouCO125>\n      <TINCO159>GB602070107000</TINCO159>\n    </TRACONCO1>\n    <TRACONCE1>\n      <NamCE17>DROFL POTTERY</NamCE17>\n      <StrAndNumCE122>125 Psuedopolis Yard</StrAndNumCE122>\n      <PosCodCE123>SS99 1AA</PosCodCE123>\n      <CitCE124>Ank-Morpork</CitCE124>\n      <CouCE125>GB</CouCE125>\n      <NADLNGCE>EN</NADLNGCE>\n      <TINCE159>GB658120050000</TINCE159>\n    </TRACONCE1>\n    <CUSOFFDEPEPT>\n      <RefNumEPT1>GB000060</RefNumEPT1>\n    </CUSOFFDEPEPT>\n    <CUSOFFTRARNS>\n      <RefNumRNS1>FR001260</RefNumRNS1>\n      <ArrTimTRACUS085>201909160100</ArrTimTRACUS085>\n    </CUSOFFTRARNS>\n    <CUSOFFDESEST>\n      <RefNumEST1>IT021100</RefNumEST1>\n    </CUSOFFDESEST>\n    <SEAINFSLI>\n      <SeaNumSLI2>1</SeaNumSLI2>\n      <SEAIDSID>\n        <SeaIdeSID1>Seal001</SeaIdeSID1>\n        <SeaIdeSID1LNG>EN</SeaIdeSID1LNG>\n      </SEAIDSID>\n    </SEAINFSLI>\n    <GUAGUA>\n      <GuaTypGUA1>3</GuaTypGUA1>\n      <GUAREFREF>\n        <GuaRefNumGRNREF1>default</GuaRefNumGRNREF1>\n        <OthGuaRefREF4>EU_EXIT</OthGuaRefREF4>\n        <AccCodREF6>test</AccCodREF6>\n      </GUAREFREF>\n    </GUAGUA>\n    <GOOITEGDS>\n      <IteNumGDS7>1</IteNumGDS7>\n      <ComCodTarCodGDS10>default</ComCodTarCodGDS10>\n      <DecTypGDS15>default</DecTypGDS15>\n      <GooDesGDS23>Flowers</GooDesGDS23>\n      <GooDesGDS23LNG>EN</GooDesGDS23LNG>\n      <GroMasGDS46>1000</GroMasGDS46>\n      <NetMasGDS48>999</NetMasGDS48>\n      <CouOfDesGDS59>ex</CouOfDesGDS59>\n      <PREADMREFAR2>\n        <PreDocTypAR21>T2</PreDocTypAR21>\n        <PreDocRefAR26>EU_EXIT-T2</PreDocRefAR26>\n        <PreDocRefLNG>EN</PreDocRefLNG>\n        <ComOfInfAR29>default</ComOfInfAR29>\n        <ComOfInfAR29LNG>EN</ComOfInfAR29LNG>\n      </PREADMREFAR2>\n      <PRODOCDC2>\n        <DocTypDC21>720</DocTypDC21>\n        <DocRefDC23>EU_EXIT</DocRefDC23>\n        <DocRefDCLNG>EN</DocRefDCLNG>\n        <ComOfInfDC25>default</ComOfInfDC25>\n        <ComOfInfDC25LNG>EN</ComOfInfDC25LNG>\n      </PRODOCDC2>\n      <PACGS2>\n        <MarNumOfPacGS21>Bloomingales</MarNumOfPacGS21>\n        <MarNumOfPacGS21LNG>EN</MarNumOfPacGS21LNG>\n        <KinOfPacGS23>BX</KinOfPacGS23>\n        <NumOfPacGS24>1</NumOfPacGS24>\n      </PACGS2>\n    </GOOITEGDS>\n  </CC015B>"
          |}""".stripMargin)

      status(result) mustBe ACCEPTED
      contentAsString(result) mustEqual expectedJson.toString()
      headers(result) must contain (LOCATION -> routes.DeparturesController.getDeparture("123").urlWithContext)
    }

    "must return UnsupportedMediaType when Content-Type is JSON" in {
      when(mockDepartureConnector.post(any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123"))) ))

      val request = FakeRequest(method = "POST", uri = "/movements/departures", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json")), body = AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when no Content-Type specified" in {
      when(mockDepartureConnector.post(any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123"))) ))

      val request = FakeRequest(method = "POST", uri = "/movements/departures", headers = FakeHeaders(Nil), body = AnyContentAsEmpty)

      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }

    "must return UnsupportedMediaType when empty XML payload is sent" in {
      when(mockDepartureConnector.post(any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, JsNull, Map(LOCATION -> Seq("/transits-movements-trader-at-departure/movements/departures/123")))))

      val request = FakeRequest(method = "POST", uri = "/movements/departures", headers = FakeHeaders(), body = AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
    }
  }

  "GET  /movements/departures/:departureId" - {
    "return 200 with json body of departure" in {
      when(mockDepartureConnector.get(any())(any(), any(), any()))
        .thenReturn(Future.successful(Right(sourceDeparture)))

      val request = FakeRequest("GET", routes.DeparturesController.getDeparture("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedDepartureResult.toString()
    }

    "return 404 if downstream return 404" in {
      when(mockDepartureConnector.get(any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(404, ""))))

      val request = FakeRequest("GET", routes.DeparturesController.getDeparture("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe NOT_FOUND
    }

    "return 500 for other downstream errors" in {
      when(mockDepartureConnector.get(any())(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(INTERNAL_SERVER_ERROR, ""))))

      val request = FakeRequest("GET", routes.DeparturesController.getDeparture("123").url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "GET /movements/departures/" - {

    "return 200 with json body of a sequence of departures" in {
      when(mockDepartureConnector.getForEori()(any(), any(), any()))
        .thenReturn(Future.successful(Right(Departures(Seq(sourceDeparture, sourceDeparture, sourceDeparture)))))

      val request = FakeRequest("GET", routes.DeparturesController.getDeparturesForEori.url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedDeparture.toString()
    }

    "return 200 with empty list if that is provided" in {
      when(mockDepartureConnector.getForEori()(any(), any(), any()))
        .thenReturn(Future.successful(Right(Departures(Nil))))

      val request = FakeRequest("GET", routes.DeparturesController.getDeparturesForEori.url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      val expectedJson = Json.parse(
        """
          |{
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/departures"
          |    }
          |  },
          |  "_embedded": {
          |    "departures": []
          |  }
          |}""".stripMargin)

      status(result) mustBe OK
      contentAsString(result) mustEqual expectedJson.toString()
    }

    "return 500 for downstream errors" in {
      when(mockDepartureConnector.getForEori()(any(), any(), any()))
        .thenReturn(Future.successful(Left(HttpResponse(INTERNAL_SERVER_ERROR, ""))))

      val request = FakeRequest("GET", routes.DeparturesController.getDeparturesForEori.url, headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)
      val result = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

}
