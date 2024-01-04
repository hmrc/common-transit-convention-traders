/*
 * Copyright 2023 HM Revenue & Customs
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
import models.domain.ArrivalId
import models.domain.ArrivalWithMessages
import models.domain.MovementMessage
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json

import java.time.LocalDateTime

class HateoasResponseArrivalWithMessagesSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterEach
    with TestXml {

  "HateoasResponseArrivalWithMessages" - {
    "must generate correct message structure for messages" in {
      val arrivalWithMessages = ArrivalWithMessages(
        ArrivalId(3),
        "loc",
        "messageLoc",
        "mrn",
        LocalDateTime.of(2020, 10, 10, 10, 10, 10),
        LocalDateTime.of(2020, 12, 12, 12, 12, 12),
        Seq(
          MovementMessage(
            "//customs/transits/movements/departures/1/messages/1",
            LocalDateTime.of(2020, 12, 12, 12, 12, 15),
            "IE007",
            CC007A,
            Some(LocalDateTime.of(2020, 12, 12, 12, 12, 15))
          )
        )
      )

      val result = HateoasResponseArrivalWithMessages(arrivalWithMessages)

      val expectedJson = Json.parse("""
          |{
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/arrivals/3/messages"
          |    }
          |  },
          |  "_embedded": {
          |    "messages": [
          |      {
          |        "_links": {
          |          "self": {
          |            "href": "/customs/transits/movements/arrivals/3/messages/1"
          |          },
          |          "arrival": {
          |            "href": "/customs/transits/movements/arrivals/3"
          |          }
          |        },
          |        "arrivalId": "3",
          |        "messageId": "1",
          |        "received": "2020-12-12T12:12:15",
          |        "messageType": "IE007",
          |        "body": "<CC007A>\n    <SynIdeMES1>UNOC</SynIdeMES1>\n    <SynVerNumMES2>3</SynVerNumMES2>\n    <MesRecMES6>NCTS</MesRecMES6>\n    <DatOfPreMES9>20200204</DatOfPreMES9>\n    <TimOfPreMES10>1302</TimOfPreMES10>\n    <IntConRefMES11>WE202002046</IntConRefMES11>\n    <AppRefMES14>NCTS</AppRefMES14>\n    <TesIndMES18>0</TesIndMES18>\n    <MesIdeMES19>1</MesIdeMES19>\n    <MesTypMES20>GB007A</MesTypMES20>\n    <HEAHEA>\n      <DocNumHEA5>99IT9876AB88901209</DocNumHEA5>\n      <CusSubPlaHEA66>EXAMPLE1</CusSubPlaHEA66>\n      <ArrNotPlaHEA60>NW16XE</ArrNotPlaHEA60>\n      <ArrNotPlaHEA60LNG>EN</ArrNotPlaHEA60LNG>\n      <ArrAgrLocOfGooHEA63LNG>EN</ArrAgrLocOfGooHEA63LNG>\n      <SimProFlaHEA132>0</SimProFlaHEA132>\n      <ArrNotDatHEA141>20200204</ArrNotDatHEA141>\n    </HEAHEA>\n    <TRADESTRD>\n      <NamTRD7>EXAMPLE2</NamTRD7>\n      <StrAndNumTRD22>Baker Street</StrAndNumTRD22>\n      <PosCodTRD23>NW16XE</PosCodTRD23>\n      <CitTRD24>London</CitTRD24>\n      <CouTRD25>GB</CouTRD25>\n      <NADLNGRD>EN</NADLNGRD>\n      <TINTRD59>EXAMPLE3</TINTRD59>\n    </TRADESTRD>\n    <CUSOFFPREOFFRES>\n      <RefNumRES1>GB000128</RefNumRES1>\n    </CUSOFFPREOFFRES>\n  </CC007A>"
          |      }
          |    ],
          |    "arrival": {
          |      "id": "3",
          |      "created": "2020-10-10T10:10:10",
          |      "updated": "2020-12-12T12:12:12",
          |      "movementReferenceNumber": "mrn",
          |      "_links": {
          |        "self": {
          |          "href": "/customs/transits/movements/arrivals/3"
          |        },
          |        "messages": {
          |          "href": "/customs/transits/movements/arrivals/3/messages"
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin)

      expectedJson mustEqual Json.toJson(result)
    }

    "must generate correct message structure for no messages" in {
      val arrivalWithMessages = ArrivalWithMessages(
        ArrivalId(3),
        "loc",
        "messageLoc",
        "mrn",
        LocalDateTime.of(2020, 10, 10, 10, 10, 10),
        LocalDateTime.of(2020, 12, 12, 12, 12, 12),
        Nil
      )

      val result = HateoasResponseArrivalWithMessages(arrivalWithMessages)

      val expectedJson = Json.parse("""
          |{
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/arrivals/3/messages"
          |    }
          |  },
          |  "_embedded": {
          |    "messages": [],
          |    "arrival": {
          |      "id": "3",
          |      "created": "2020-10-10T10:10:10",
          |      "updated": "2020-12-12T12:12:12",
          |      "movementReferenceNumber": "mrn",
          |      "_links": {
          |        "self": {
          |          "href": "/customs/transits/movements/arrivals/3"
          |        },
          |        "messages": {
          |          "href": "/customs/transits/movements/arrivals/3/messages"
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin)

      expectedJson mustEqual Json.toJson(result)
    }
  }
}
