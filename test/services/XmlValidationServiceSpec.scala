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

package services

import models.request.{ArrivalNotificationXSD, DepartureDeclarationXSD}
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class XmlValidationServiceSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  private val xmlValidationService = new XmlValidationService

  "validate" - {

    "must be successful when validating a valid ArrivalNotification xml" - {

      "with minimal completed fields" in {
        val xml = buildIE007Xml()

        xmlValidationService.validate(xml, ArrivalNotificationXSD) mustBe a[Right[_, _]]
      }

      "with an enroute event" in {

        forAll(arbitrary[Boolean], arbitrary[Boolean], arbitrary[Boolean], arbitrary[Boolean]) {
          (withIncident, withContainer, withVehicle, withSeals) =>
            val xml = buildIE007Xml(withEnrouteEvent = true, withIncident, withContainer, withVehicle, withSeals)
            xmlValidationService.validate(xml, ArrivalNotificationXSD) mustBe a[Right[_, _]]
        }
      }
    }

    "must be successful when validating a valid DepartureDeclaration xml" - {

      "with minimal completed fields" in {
        val xml = buildIE015Xml()

        xmlValidationService.validate(xml, DepartureDeclarationXSD) mustBe a[Right[_, _]]
      }

    }

    "must fail when validating an invalid DepartureDeclaration xml" - {

      "with missing mandatory elements" in {
        val xml = "<CC015B></CC015B>"

        val expectedMessage =
          "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc015b.xsd'. Detailed error below:\ncvc-complex-type.2.4.b: The content of element 'CC015B' is not complete. One of '{SynIdeMES1}' is expected."

        xmlValidationService.validate(xml, DepartureDeclarationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }

      "with invalid fields" in {

        val xml =
          """
            |<CC015B>
            |    <SynIdeMES1>11111111111111</SynIdeMES1>
            |</CC015B>
          """.stripMargin

        val expectedMessage =
          "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc015b.xsd'. Detailed error below:\ncvc-pattern-valid: Value '11111111111111' is not facet-valid with respect to pattern '[a-zA-Z]{4}' for type 'Alpha_4'."

        xmlValidationService.validate(xml, DepartureDeclarationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }

      "with too long Message Type" in {

        val xml = buildIE015Xml(withMessageType = "toolong")

        val expectedMessage =
          "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc015b.xsd'. Detailed error below:\ncvc-pattern-valid: Value 'toolong' is not facet-valid with respect to pattern '.{6}' for type 'Alphanumeric_6'."

        xmlValidationService.validate(xml, DepartureDeclarationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }

      "with too short Message Type" in {

        val xml = buildIE015Xml(withMessageType = "toos")

        val expectedMessage =
          "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc015b.xsd'. Detailed error below:\ncvc-pattern-valid: Value 'toos' is not facet-valid with respect to pattern '.{6}' for type 'Alphanumeric_6'."

        xmlValidationService.validate(xml, DepartureDeclarationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }
    }

    "must fail when validating an invalid ArrivalNotification xml" - {

      "with missing mandatory elements" in {
        val xml = "<CC007A></CC007A>"

        val expectedMessage =
          "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc007a.xsd'. Detailed error below:\ncvc-complex-type.2.4.b: The content of element 'CC007A' is not complete. One of '{SynIdeMES1}' is expected."

        xmlValidationService.validate(xml, ArrivalNotificationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }

      "with invalid fields" in {

        val xml =
          """
            |<CC007A>
            |    <SynIdeMES1>11111111111111</SynIdeMES1>
            |</CC007A>
          """.stripMargin

        val expectedMessage =
          "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc007a.xsd'. Detailed error below:\ncvc-pattern-valid: Value '11111111111111' is not facet-valid with respect to pattern '[a-zA-Z]{4}' for type 'Alpha_4'."

        xmlValidationService.validate(xml, ArrivalNotificationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }

      "with too long Message Type" in {

        val xml = buildIE007Xml(withMessageType = "toolong")

        val expectedMessage =
          "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc007a.xsd'. Detailed error below:\ncvc-pattern-valid: Value 'toolong' is not facet-valid with respect to pattern '.{6}' for type 'Alphanumeric_6'."

        xmlValidationService.validate(xml, ArrivalNotificationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }

      "with too short Message Type" in {

        val xml = buildIE007Xml(withMessageType = "toos")

        val expectedMessage =
          "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc007a.xsd'. Detailed error below:\ncvc-pattern-valid: Value 'toos' is not facet-valid with respect to pattern '.{6}' for type 'Alphanumeric_6'."

        xmlValidationService.validate(xml, ArrivalNotificationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }
    }
  }

  private def buildIE007Xml(
    withEnrouteEvent: Boolean = false,
    withIncident: Boolean = false,
    withContainerTranshipment: Boolean = false,
    withVehicularTranshipment: Boolean = false,
    withSeals: Boolean = false,
    withMessageType: String = "GB007A"
  ): String = {

    val enrouteEvent = {
      if (withEnrouteEvent)
        buildEnrouteEvent(withIncident, withContainerTranshipment, withVehicularTranshipment, withSeals)
      else ""
    }

    s"""
       |<CC007A>
       |    <SynIdeMES1>UNOC</SynIdeMES1>
       |    <SynVerNumMES2>3</SynVerNumMES2>
       |    <MesRecMES6>NCTS</MesRecMES6>
       |    <DatOfPreMES9>20190912</DatOfPreMES9>
       |    <TimOfPreMES10>1445</TimOfPreMES10>
       |    <IntConRefMES11>WE190912102534</IntConRefMES11>
       |    <AppRefMES14>NCTS</AppRefMES14>
       |    <TesIndMES18>0</TesIndMES18>
       |    <MesIdeMES19>1</MesIdeMES19>
       |    <MesTypMES20>$withMessageType</MesTypMES20>
       |    <HEAHEA>
       |        <DocNumHEA5>19IT02110010007827</DocNumHEA5>
       |        <ArrNotPlaHEA60>DOVER</ArrNotPlaHEA60>
       |        <SimProFlaHEA132>0</SimProFlaHEA132>
       |        <ArrNotDatHEA141>20191110</ArrNotDatHEA141>
       |    </HEAHEA>
       |    <TRADESTRD>
       |        <TINTRD59>GB163910077000</TINTRD59>
       |    </TRADESTRD>
       |    <CUSOFFPREOFFRES>
       |        <RefNumRES1>GB000060</RefNumRES1>
       |    </CUSOFFPREOFFRES>
       |    $enrouteEvent
       |</CC007A>
        """.stripMargin
  }

  private def buildIE015Xml( withMessageType: String = "GB015B"
                           ): String = {

    s"""
       |  <CC015B>
       |    <SynIdeMES1>UNOC</SynIdeMES1>
       |    <SynVerNumMES2>3</SynVerNumMES2>
       |    <MesRecMES6>NCTS</MesRecMES6>
       |    <DatOfPreMES9>20201217</DatOfPreMES9>
       |    <TimOfPreMES10>1340</TimOfPreMES10>
       |    <IntConRefMES11>17712576475433</IntConRefMES11>
       |    <AppRefMES14>NCTS</AppRefMES14>
       |    <MesIdeMES19>1</MesIdeMES19>
       |    <MesTypMES20>$withMessageType</MesTypMES20>
       |    <HEAHEA>
       |      <RefNumHEA4>GUATEST1201217134032</RefNumHEA4>
       |      <TypOfDecHEA24>T1</TypOfDecHEA24>
       |      <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>
       |      <AutLocOfGooCodHEA41>954131533-GB60DEP</AutLocOfGooCodHEA41>
       |      <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>
       |      <IdeOfMeaOfTraAtDHEA78>NC15 REG</IdeOfMeaOfTraAtDHEA78>
       |      <NatOfMeaOfTraAtDHEA80>GB</NatOfMeaOfTraAtDHEA80>
       |      <ConIndHEA96>0</ConIndHEA96>
       |      <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>
       |      <TotNumOfIteHEA305>1</TotNumOfIteHEA305>
       |      <TotNumOfPacHEA306>10</TotNumOfPacHEA306>
       |      <TotGroMasHEA307>1000</TotGroMasHEA307>
       |      <DecDatHEA383>20201217</DecDatHEA383>
       |      <DecPlaHEA394>Dover</DecPlaHEA394>
       |    </HEAHEA>
       |    <TRAPRIPC1>
       |      <NamPC17>NCTS UK TEST LAB HMCE</NamPC17>
       |      <StrAndNumPC122>11TH FLOOR, ALEX HOUSE, VICTORIA AV</StrAndNumPC122>
       |      <PosCodPC123>SS99 1AA</PosCodPC123>
       |      <CitPC124>SOUTHEND-ON-SEA, ESSEX</CitPC124>
       |      <CouPC125>GB</CouPC125>
       |      <TINPC159>GB954131533000</TINPC159>
       |    </TRAPRIPC1>
       |    <TRACONCO1>
       |      <NamCO17>NCTS UK TEST LAB HMCE</NamCO17>
       |      <StrAndNumCO122>11TH FLOOR, ALEX HOUSE, VICTORIA AV</StrAndNumCO122>
       |      <PosCodCO123>SS99 1AA</PosCodCO123>
       |      <CitCO124>SOUTHEND-ON-SEA, ESSEX</CitCO124>
       |      <CouCO125>GB</CouCO125>
       |      <TINCO159>GB954131533000</TINCO159>
       |    </TRACONCO1>
       |    <TRACONCE1>
       |      <NamCE17>NCTS UK TEST LAB HMCE</NamCE17>
       |      <StrAndNumCE122>ITALIAN OFFICE</StrAndNumCE122>
       |      <PosCodCE123>IT99 1IT</PosCodCE123>
       |      <CitCE124>MILAN</CitCE124>
       |      <CouCE125>IT</CouCE125>
       |      <TINCE159>IT11ITALIANC11</TINCE159>
       |    </TRACONCE1>
       |    <CUSOFFDEPEPT>
       |      <RefNumEPT1>GB000060</RefNumEPT1>
       |    </CUSOFFDEPEPT>
       |    <CUSOFFTRARNS>
       |      <RefNumRNS1>FR001260</RefNumRNS1>
       |      <ArrTimTRACUS085>202012191340</ArrTimTRACUS085>
       |    </CUSOFFTRARNS>
       |    <CUSOFFDESEST>
       |      <RefNumEST1>IT018100</RefNumEST1>
       |    </CUSOFFDESEST>
       |    <CONRESERS>
       |      <ConResCodERS16>A3</ConResCodERS16>
       |      <DatLimERS69>20201225</DatLimERS69>
       |    </CONRESERS>
       |    <SEAINFSLI>
       |      <SeaNumSLI2>1</SeaNumSLI2>
       |      <SEAIDSID>
       |        <SeaIdeSID1>NCTS001</SeaIdeSID1>
       |      </SEAIDSID>
       |    </SEAINFSLI>
       |    <GUAGUA>
       |      <GuaTypGUA1>0</GuaTypGUA1>
       |      <GUAREFREF>
       |        <GuaRefNumGRNREF1>20GB0000010000H72</GuaRefNumGRNREF1>
       |        <AccCodREF6>AC01</AccCodREF6>
       |      </GUAREFREF>
       |    </GUAGUA>
       |    <GOOITEGDS>
       |      <IteNumGDS7>1</IteNumGDS7>
       |      <GooDesGDS23>Wheat</GooDesGDS23>
       |      <GooDesGDS23LNG>EN</GooDesGDS23LNG>
       |      <GroMasGDS46>1000</GroMasGDS46>
       |      <NetMasGDS48>950</NetMasGDS48>
       |      <SPEMENMT2>
       |        <AddInfMT21>20GB0000010000H72</AddInfMT21>
       |        <AddInfCodMT23>CAL</AddInfCodMT23>
       |      </SPEMENMT2>
       |      <PACGS2>
       |        <MarNumOfPacGS21>AB234</MarNumOfPacGS21>
       |        <KinOfPacGS23>BX</KinOfPacGS23>
       |        <NumOfPacGS24>10</NumOfPacGS24>
       |      </PACGS2>
       |    </GOOITEGDS>
       |  </CC015B>
        """.stripMargin
  }

  private val buildIncident: String =
    """
      |<INCINC>
      | <IncFlaINC3>1</IncFlaINC3>
      | <IncInfINC4>Incident details</IncInfINC4>
      | <IncInfINC4LNG>GB</IncInfINC4LNG>
      | <EndDatINC6>20191110</EndDatINC6>
      | <EndAutINC7>Authority</EndAutINC7>
      | <EndAutINC7LNG>GB</EndAutINC7LNG>
      | <EndPlaINC10>Endorsement place</EndPlaINC10>
      | <EndPlaINC10LNG>GB</EndPlaINC10LNG>
      | <EndCouINC12>GB</EndCouINC12>
      |</INCINC>
      |""".stripMargin

  private val buildContainerTranshipment: String =
    """
      |<TRASHP>
      | <EndDatSHP60>20191110</EndDatSHP60>
      | <EndAutSHP61>Authority</EndAutSHP61>
      | <EndAutSHP61LNG>GB</EndAutSHP61LNG>
      | <EndPlaSHP63>Endorsement place</EndPlaSHP63>
      | <EndPlaSHP63LNG>GB</EndPlaSHP63LNG>
      | <EndCouSHP65>GB</EndCouSHP65>
      | <CONNR3>
      |   <ConNumNR31>Container id</ConNumNR31>
      | </CONNR3>
      |</TRASHP>
      |""".stripMargin

  private val buildVehicularTranshipment: String =
    """
      |<TRASHP>
      | <NewTraMeaIdeSHP26>Transport identity</NewTraMeaIdeSHP26>
      | <NewTraMeaIdeSHP26LNG>GB</NewTraMeaIdeSHP26LNG>
      | <NewTraMeaNatSHP54>GB</NewTraMeaNatSHP54>
      | <EndDatSHP60>20191110</EndDatSHP60>
      | <EndAutSHP61>Authority</EndAutSHP61>
      | <EndAutSHP61LNG>GB</EndAutSHP61LNG>
      | <EndPlaSHP63>Endorsement place</EndPlaSHP63>
      | <EndPlaSHP63LNG>GB</EndPlaSHP63LNG>
      | <EndCouSHP65>GB</EndCouSHP65>
      | <CONNR3>
      |   <ConNumNR31>Container id</ConNumNR31>
      | </CONNR3>
      |</TRASHP>
      |""".stripMargin

  def transhipment(isVehicular: Boolean, hasContainer: Boolean) =
    s"""
      |<TRASHP>
      | ${if (isVehicular) { vehicular }
    else ""}
      | <EndDatSHP60>20191110</EndDatSHP60>
      | <EndAutSHP61>Authority</EndAutSHP61>
      | <EndAutSHP61LNG>GB</EndAutSHP61LNG>
      | <EndPlaSHP63>Endorsement place</EndPlaSHP63>
      | <EndPlaSHP63LNG>GB</EndPlaSHP63LNG>
      | <EndCouSHP65>GB</EndCouSHP65>
      | ${if (hasContainer) { container }
    else ""}
      |</TRASHP>
      |""".stripMargin

  val vehicular: String = """
                    | <NewTraMeaIdeSHP26>Transport identity</NewTraMeaIdeSHP26>
                    | <NewTraMeaIdeSHP26LNG>GB</NewTraMeaIdeSHP26LNG>
                    | <NewTraMeaNatSHP54>GB</NewTraMeaNatSHP54>
                    | """.stripMargin

  val container: String =
    """
      | <CONNR3>
      |   <ConNumNR31>Container id</ConNumNR31>
      | </CONNR3>
      |""".stripMargin

  private def buildEnrouteEvent(withIncident: Boolean, withContainerTranshipment: Boolean, withVehicularTranshipment: Boolean, withSeal: Boolean): String =
    s"""
       |<ENROUEVETEV>
       | <PlaTEV10>eventPlace</PlaTEV10>
       | <PlaTEV10LNG>GB</PlaTEV10LNG>
       | <CouTEV13>GB</CouTEV13>
       | <CTLCTL>
       |   <AlrInNCTCTL29>1</AlrInNCTCTL29>
       | </CTLCTL>
       ${buildEventDetails(withIncident, withContainerTranshipment, withVehicularTranshipment, withSeal)}
       |</ENROUEVETEV>
    """.stripMargin

  val buildSeals: String =
    s"""
       | <SEAINFSF1>
       | <SeaNumSF12>2</SeaNumSF12>
       | <SEAIDSI1>
       | <SeaIdeSI11>seal1</SeaIdeSI11>
       | <SeaIdeSI11LNG>EN</SeaIdeSI11LNG>
       | </SEAIDSI1>
       | <SEAIDSI1>
       | <SeaIdeSI11>seal2</SeaIdeSI11>
       | <SeaIdeSI11LNG>EN</SeaIdeSI11LNG>
       | </SEAIDSI1>
       | </SEAINFSF1>
       |""".stripMargin

  private def buildEventDetails(withIncident: Boolean, withContainerTranshipment: Boolean, withVehicularTranshipment: Boolean, withSeal: Boolean): String = {
    val incident: String = if (withIncident) buildIncident else ""
    val seals: String    = if (withSeal) buildSeals else ""

    s"""
       |$incident
       |$seals
       |${transhipment(withVehicularTranshipment, withContainerTranshipment)}
       |""".stripMargin
  }
}
