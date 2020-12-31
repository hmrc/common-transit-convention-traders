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

package models.response

import data.TestXml

import java.time.LocalDateTime
import models.domain.{DepartureWithMessages, MovementMessage}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json

class HateaosResponseDepartureWithMessagesSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach with TestXml {

  "HateaosResponseDepartureWithMessages" - {
    "must generate correct message structure for messages" in {
      val departureWithMessages = DepartureWithMessages(3,
        "loc",
        "messageLoc",
        Some("mrn"),
        "status",
        LocalDateTime.of(2020, 10, 10, 10, 10, 10),
        LocalDateTime.of(2020, 12, 12, 12, 12, 12),
        Seq(MovementMessage(
          "//customs/transits/movements/departures/1/messages/1",
          LocalDateTime.of(2020, 12, 12, 12, 12, 15),
          "IE015",
          CC015B
        )))

      val expectedJson = Json.parse(
        """
          |{
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/departures/3/messages"
          |    }
          |  },
          |  "_embedded": {
          |    "messages": [
          |      {
          |        "_links": {
          |          "self": {
          |            "href": "/customs/transits/movements/departures/3/messages/1"
          |          },
          |          "departure": {
          |            "href": "/customs/transits/movements/departures/3"
          |          }
          |        },
          |        "departureId": "3",
          |        "messageId": "1",
          |        "received": "2020-12-12T12:12:15",
          |        "messageType": "IE015",
          |        "body": "<CC015B>\n    <SynIdeMES1>UNOC</SynIdeMES1>\n    <SynVerNumMES2>3</SynVerNumMES2>\n    <MesRecMES6>NCTS</MesRecMES6>\n    <DatOfPreMES9>20190912</DatOfPreMES9>\n    <TimOfPreMES10>1222</TimOfPreMES10>\n    <IntConRefMES11>WE190912102530</IntConRefMES11>\n    <AppRefMES14>NCTS</AppRefMES14>\n    <TesIndMES18>0</TesIndMES18>\n    <MesIdeMES19>1</MesIdeMES19>\n    <MesTypMES20>GB015B</MesTypMES20>\n    <HEAHEA>\n      <RefNumHEA4>01CTC201909121215</RefNumHEA4>\n      <TypOfDecHEA24>T2</TypOfDecHEA24>\n      <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>\n      <AgrLocOfGooCodHEA38>default</AgrLocOfGooCodHEA38>\n      <AgrLocOfGooHEA39>default</AgrLocOfGooHEA39>\n      <AgrLocOfGooHEA39LNG>EN</AgrLocOfGooHEA39LNG>\n      <AutLocOfGooCodHEA41>default</AutLocOfGooCodHEA41>\n      <PlaOfLoaCodHEA46>DOVER</PlaOfLoaCodHEA46>\n      <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>\n      <CusSubPlaHEA66>default</CusSubPlaHEA66>\n      <InlTraModHEA75>20</InlTraModHEA75>\n      <IdeOfMeaOfTraAtDHEA78>EU_EXIT</IdeOfMeaOfTraAtDHEA78>\n      <IdeOfMeaOfTraAtDHEA78LNG>EN</IdeOfMeaOfTraAtDHEA78LNG>\n      <IdeOfMeaOfTraCroHEA85>EU_EXIT</IdeOfMeaOfTraCroHEA85>\n      <IdeOfMeaOfTraCroHEA85LNG>EN</IdeOfMeaOfTraCroHEA85LNG>\n      <ConIndHEA96>0</ConIndHEA96>\n      <DiaLanIndAtDepHEA254>EN</DiaLanIndAtDepHEA254>\n      <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>\n      <TotNumOfIteHEA305>1</TotNumOfIteHEA305>\n      <TotNumOfPacHEA306>1</TotNumOfPacHEA306>\n      <TotGroMasHEA307>1000</TotGroMasHEA307>\n      <DecDatHEA383>20190912</DecDatHEA383>\n      <DecPlaHEA394>DOVER</DecPlaHEA394>\n      <DecPlaHEA394LNG>EN</DecPlaHEA394LNG>\n    </HEAHEA>\n    <TRAPRIPC1>\n      <NamPC17>CITY WATCH SHIPPING</NamPC17>\n      <StrAndNumPC122>125 Psuedopolis Yard</StrAndNumPC122>\n      <PosCodPC123>SS99 1AA</PosCodPC123>\n      <CitPC124>Ank-Morpork</CitPC124>\n      <CouPC125>GB</CouPC125>\n      <NADLNGPC>EN</NADLNGPC>\n      <TINPC159>GB652420267000</TINPC159>\n    </TRAPRIPC1>\n    <TRACONCO1>\n      <NamCO17>QUIRM ENGINEERING</NamCO17>\n      <StrAndNumCO122>125 Psuedopolis Yard</StrAndNumCO122>\n      <PosCodCO123>SS99 1AA</PosCodCO123>\n      <CitCO124>Ank-Morpork</CitCO124>\n      <CouCO125>GB</CouCO125>\n      <TINCO159>GB602070107000</TINCO159>\n    </TRACONCO1>\n    <TRACONCE1>\n      <NamCE17>DROFL POTTERY</NamCE17>\n      <StrAndNumCE122>125 Psuedopolis Yard</StrAndNumCE122>\n      <PosCodCE123>SS99 1AA</PosCodCE123>\n      <CitCE124>Ank-Morpork</CitCE124>\n      <CouCE125>GB</CouCE125>\n      <NADLNGCE>EN</NADLNGCE>\n      <TINCE159>GB658120050000</TINCE159>\n    </TRACONCE1>\n    <CUSOFFDEPEPT>\n      <RefNumEPT1>GB000060</RefNumEPT1>\n    </CUSOFFDEPEPT>\n    <CUSOFFTRARNS>\n      <RefNumRNS1>FR001260</RefNumRNS1>\n      <ArrTimTRACUS085>201909160100</ArrTimTRACUS085>\n    </CUSOFFTRARNS>\n    <CUSOFFDESEST>\n      <RefNumEST1>IT021100</RefNumEST1>\n    </CUSOFFDESEST>\n    <SEAINFSLI>\n      <SeaNumSLI2>1</SeaNumSLI2>\n      <SEAIDSID>\n        <SeaIdeSID1>Seal001</SeaIdeSID1>\n        <SeaIdeSID1LNG>EN</SeaIdeSID1LNG>\n      </SEAIDSID>\n    </SEAINFSLI>\n    <GUAGUA>\n      <GuaTypGUA1>3</GuaTypGUA1>\n      <GUAREFREF>\n        <GuaRefNumGRNREF1>default</GuaRefNumGRNREF1>\n        <OthGuaRefREF4>EU_EXIT</OthGuaRefREF4>\n        <AccCodREF6>test</AccCodREF6>\n      </GUAREFREF>\n    </GUAGUA>\n    <GOOITEGDS>\n      <IteNumGDS7>1</IteNumGDS7>\n      <ComCodTarCodGDS10>default</ComCodTarCodGDS10>\n      <DecTypGDS15>default</DecTypGDS15>\n      <GooDesGDS23>Flowers</GooDesGDS23>\n      <GooDesGDS23LNG>EN</GooDesGDS23LNG>\n      <GroMasGDS46>1000</GroMasGDS46>\n      <NetMasGDS48>999</NetMasGDS48>\n      <CouOfDesGDS59>ex</CouOfDesGDS59>\n      <PREADMREFAR2>\n        <PreDocTypAR21>T2</PreDocTypAR21>\n        <PreDocRefAR26>EU_EXIT-T2</PreDocRefAR26>\n        <PreDocRefLNG>EN</PreDocRefLNG>\n        <ComOfInfAR29>default</ComOfInfAR29>\n        <ComOfInfAR29LNG>EN</ComOfInfAR29LNG>\n      </PREADMREFAR2>\n      <PRODOCDC2>\n        <DocTypDC21>720</DocTypDC21>\n        <DocRefDC23>EU_EXIT</DocRefDC23>\n        <DocRefDCLNG>EN</DocRefDCLNG>\n        <ComOfInfDC25>default</ComOfInfDC25>\n        <ComOfInfDC25LNG>EN</ComOfInfDC25LNG>\n      </PRODOCDC2>\n      <PACGS2>\n        <MarNumOfPacGS21>Bloomingales</MarNumOfPacGS21>\n        <MarNumOfPacGS21LNG>EN</MarNumOfPacGS21LNG>\n        <KinOfPacGS23>BX</KinOfPacGS23>\n        <NumOfPacGS24>1</NumOfPacGS24>\n      </PACGS2>\n    </GOOITEGDS>\n  </CC015B>"
          |      }
          |    ],
          |    "departure": {
          |      "id": "3",
          |      "created": "2020-10-10T10:10:10",
          |      "updated": "2020-12-12T12:12:12",
          |      "movementReferenceNumber": "mrn",
          |      "status": "status",
          |      "_links": {
          |        "self": {
          |          "href": "/customs/transits/movements/departures/3"
          |        },
          |        "messages": {
          |          "href": "/customs/transits/movements/departures/3/messages"
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin)

      val result = HateaosResponseDepartureWithMessages(departureWithMessages)

      expectedJson mustEqual Json.toJson(result)
    }

    "must generate correct message structure for empty messages" in {
      val departureWithMessages = DepartureWithMessages(3,
        "loc",
        "messageLoc",
        Some("mrn"),
        "status",
        LocalDateTime.of(2020, 10, 10, 10, 10, 10),
        LocalDateTime.of(2020, 12, 12, 12, 12, 12),
        Nil)

      val expectedJson = Json.parse(
        """
          |{
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/departures/3/messages"
          |    }
          |  },
          |  "_embedded": {
          |    "messages": [],
          |    "departure": {
          |      "id": "3",
          |      "created": "2020-10-10T10:10:10",
          |      "updated": "2020-12-12T12:12:12",
          |      "movementReferenceNumber": "mrn",
          |      "status": "status",
          |      "_links": {
          |        "self": {
          |          "href": "/customs/transits/movements/departures/3"
          |        },
          |        "messages": {
          |          "href": "/customs/transits/movements/departures/3/messages"
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin)

      val result = HateaosResponseDepartureWithMessages(departureWithMessages)

      expectedJson mustEqual Json.toJson(result)
    }
  }
}
