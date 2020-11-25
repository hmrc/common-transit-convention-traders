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

package utils

import java.math.BigInteger

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsObject, Json}

class JsonHelperSpec extends AnyFreeSpec with Matchers {

  val fullXmlMessage = <CC015B><SynIdeMES1>UNOC</SynIdeMES1><SynVerNumMES2>3</SynVerNumMES2><MesTypMES20>GB015B</MesTypMES20><HEAHEA>  <RefNumHEA4>20GB000060100235C9</RefNumHEA4>  <TypOfDecHEA24>T1</TypOfDecHEA24>  <DecDatHEA383>20201007</DecDatHEA383>  <DecPlaHEA394LNG>EN</DecPlaHEA394LNG></HEAHEA><TRAPRIPC1>  <NamPC17>CITY WATCH SHIPPING</NamPC17>  <StrAndNumPC122>125 Psuedopolis Yard</StrAndNumPC122>  <NADLNGPC>EN</NADLNGPC>  <TINPC159>GB652420267000</TINPC159></TRAPRIPC1><TRACONCO1>  <NamCO17>QUIRM ENGINEERING</NamCO17>  <CouCO125>GB</CouCO125>  <TINCO159>GB602070107000</TINCO159></TRACONCO1><TRACONCE1>  <NamCE17>DROFL POTTERY</NamCE17>  <StrAndNumCE122>125 Psuedopolis Yard</StrAndNumCE122>  <PosCodCE123>SS99 1AA</PosCodCE123>  <TINCE159>GB658120050000</TINCE159></TRACONCE1><CUSOFFDEPEPT>  <RefNumEPT1>GB000060</RefNumEPT1></CUSOFFDEPEPT><CUSOFFTRARNS>  <RefNumRNS1>FR001260</RefNumRNS1>  <ArrTimTRACUS085>202010140102</ArrTimTRACUS085></CUSOFFTRARNS><CUSOFFDESEST>  <RefNumEST1>IT021100</RefNumEST1></CUSOFFDESEST><SEAINFSLI>  <SeaNumSLI2>3</SeaNumSLI2>  <SEAIDSID>    <SeaIdeSID1>Seal#001</SeaIdeSID1>    <SeaIdeSID1LNG>EN</SeaIdeSID1LNG>  </SEAIDSID>  <SEAIDSID>    <SeaIdeSID1>Seal#002</SeaIdeSID1>    <SeaIdeSID1LNG>EN</SeaIdeSID1LNG>  </SEAIDSID>  <SEAIDSID>    <SeaIdeSID1>Seal#003</SeaIdeSID1>    <SeaIdeSID1LNG>EN</SeaIdeSID1LNG>  </SEAIDSID></SEAINFSLI><GUAGUA>  <GuaTypGUA1>9</GuaTypGUA1>  <GUAREFREF>    <GuaRefNumGRNREF1>07IT00000100000Z1</GuaRefNumGRNREF1>    <AccCodREF6>AX11</AccCodREF6>  </GUAREFREF></GUAGUA><GUAGUA>  <GuaTypGUA1>8</GuaTypGUA1>  <GUAREFREF>    <GuaRefNumGRNREF1>07IT00000100000Z3</GuaRefNumGRNREF1>    <OthGuaRefREF4>EU_EXIT</OthGuaRefREF4></GUAREFREF></GUAGUA><GUAGUA>  <GuaTypGUA1>A</GuaTypGUA1>  <GUAREFREF>    <GuaRefNumGRNREF1>07IT00000100000Z9</GuaRefNumGRNREF1>    <OthGuaRefREF4>EU_EXIT_0</OthGuaRefREF4></GUAREFREF></GUAGUA></CC015B>

  "JsonHelper" - {

    "convertXmlToJson" - {

      "must convert xml to json" in {
        val xml = <xml><test1>one</test1><test1>two</test1></xml>

        val expectedResult: JsObject = Json.obj("xml" -> Json.obj("test1" -> Json.arr("one", "two")))
        val result: JsObject = JsonHelper.convertXmlToJson(xml)
        result.toString mustBe expectedResult.toString()
      }

      "must convert nested single name xml to json" in {
        val xml = <xml><test1><test2>one</test2><test2>two</test2></test1></xml>

        val expectedResult: JsObject =
          Json.obj("xml" -> Json.obj("test1" -> Json.obj("test2" -> Json.arr("one", "two"))))
        val result: JsObject = JsonHelper.convertXmlToJson(xml)
        result.toString mustBe expectedResult.toString()
      }

      "must convert nested multi name xml to json" in {
        val xml = <xml><test1><test2>one</test2><test3>two</test3></test1></xml>

        val expectedResult: JsObject =
          Json.obj("xml" -> Json.obj("test1" -> Json.obj("test2" -> "one", "test3" -> "two")))
        val result: JsObject = JsonHelper.convertXmlToJson(xml)
        result.toString mustBe expectedResult.toString()
      }

      "must convert complex xml to json" in {
        val xml = <xml><a><b>one</b><b>1</b><c>two</c></a><d>3</d><a>10</a><a>test</a></xml>

        val expectedResult: JsObject =
          Json.obj("xml" -> Json.obj(
            "a" -> Json.arr(Json.obj("b" -> Json.arr("one", 1), "c" -> "two"), 10, "test"),
            "d" -> 3
          ))
        val result: JsObject = JsonHelper.convertXmlToJson(xml)
        result.toString mustBe expectedResult.toString()
      }

      "must convert full xml message to json" in {
        val bigIntTimestamp = new BigInt(new BigInteger("202010140102"))

        val expectedResult: JsObject =
          Json.obj("CC015B" -> Json.obj(
            "SynIdeMES1" -> "UNOC",
            "GUAGUA" -> Json.arr(
              Json.obj(
                "GUAREFREF" -> Json.obj(
                  "GuaRefNumGRNREF1" -> "07IT00000100000Z1",
                  "AccCodREF6" -> "AX11"
                ),
                "GuaTypGUA1" -> 9
              ),
              Json.obj(
                "GUAREFREF" -> Json.obj(
                  "GuaRefNumGRNREF1" -> "07IT00000100000Z3",
                  "OthGuaRefREF4" -> "EU_EXIT"
                ),
                "GuaTypGUA1" -> 8
              ),
              Json.obj(
                "GUAREFREF" -> Json.obj(
                  "GuaRefNumGRNREF1" -> "07IT00000100000Z9",
                  "OthGuaRefREF4" -> "EU_EXIT_0"
                ),
                "GuaTypGUA1" -> "A"
              )
            ),
            "CUSOFFDEPEPT" -> Json.obj(
              "RefNumEPT1" -> "GB000060"
            ),
            "HEAHEA" -> Json.obj(
              "DecDatHEA383" -> 20201007,
              "DecPlaHEA394LNG" -> "EN",
              "TypOfDecHEA24" -> "T1",
              "RefNumHEA4" -> "20GB000060100235C9"
            ),
            "TRAPRIPC1" -> Json.obj(
              "NamPC17" -> "CITY WATCH SHIPPING",
              "TINPC159" -> "GB652420267000",
              "StrAndNumPC122" -> "125 Psuedopolis Yard",
              "NADLNGPC" -> "EN"
            ),
            "CUSOFFTRARNS" -> Json.obj(
              "ArrTimTRACUS085" -> bigIntTimestamp,
              "RefNumRNS1" -> "FR001260"
            ),
            "TRACONCO1" -> Json.obj(
              "CouCO125" -> "GB",
              "NamCO17" -> "QUIRM ENGINEERING",
              "TINCO159" -> "GB602070107000"
            ),
            "SEAINFSLI" -> Json.obj(
              "SEAIDSID" -> Json.arr(
                Json.obj(
                  "SeaIdeSID1" -> "Seal#001",
                  "SeaIdeSID1LNG" -> "EN"
                ),
                Json.obj(
                  "SeaIdeSID1" -> "Seal#002",
                  "SeaIdeSID1LNG" -> "EN"
                ),
                Json.obj(
                  "SeaIdeSID1" -> "Seal#003",
                  "SeaIdeSID1LNG" -> "EN"
                ),
              ),
              "SeaNumSLI2" -> 3
            ),
            "SynVerNumMES2" -> 3,
            "MesTypMES20" -> "GB015B",
            "TRACONCE1" -> Json.obj(
              "NamCE17" -> "DROFL POTTERY",
              "PosCodCE123" -> "SS99 1AA",
              "TINCE159" -> "GB658120050000",
              "StrAndNumCE122" -> "125 Psuedopolis Yard",
            ),
            "CUSOFFDESEST" -> Json.obj(
              "RefNumEST1" -> "IT021100"
            )
          ))
        val result: JsObject = JsonHelper.convertXmlToJson(fullXmlMessage)
        result.toString mustBe expectedResult.toString()
      }

    }

  }
}
