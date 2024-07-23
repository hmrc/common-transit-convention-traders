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

package services

import config.AppConfig
import models.request.ArrivalNotificationXSD
import models.request.DeclarationCancellationRequestXSD
import models.request.DepartureDeclarationXSD
import models.request.UnloadingRemarksXSD
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.BeforeAndAfter
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.xml.NodeSeq
import scala.xml.XML

class XmlValidationServiceSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks with MockitoSugar with BeforeAndAfter {

  private val xml2001namespace     = "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\""
  private val emptyNamespace       = "xmlns=\"\""
  private val mockAppConfig        = mock[AppConfig]
  private val xmlValidationService = new XmlValidationService(mockAppConfig)

  before {
    // some tests will set this to false, but the majority of these tests should have this set to true.
    when(mockAppConfig.blockUnknownNamespaces).thenReturn(true)
  }

  "validate" - {

    "must be successful when validating a valid ArrivalNotification xml" - {

      "with minimal completed fields" in {
        val xml = buildIE007Xml()

        xmlValidationService.validate(XML.loadString(xml), ArrivalNotificationXSD) mustBe a[Right[_, _]]
      }

      "with minimal completed fields and an empty namespace" in {
        val xml = buildIE007Xml(withRootLevelAttirbutes = emptyNamespace)

        xmlValidationService.validate(XML.loadString(xml), ArrivalNotificationXSD) mustBe a[Right[_, _]]
      }

      "with an enroute event" in {

        forAll(arbitrary[Boolean], arbitrary[Boolean], arbitrary[Boolean], arbitrary[Boolean]) {
          (withIncident, withContainer, withVehicle, withSeals) =>
            val xml = buildIE007Xml(withEnrouteEvent = true, withIncident, withContainer, withVehicle, withSeals)
            xmlValidationService.validate(XML.loadString(xml), ArrivalNotificationXSD) mustBe a[Right[_, _]]
        }
      }

      "with a top level namespace when the config is NOT set to block unknown namespaces" in {

        when(mockAppConfig.blockUnknownNamespaces).thenReturn(false)
        val xml = XML.loadString(buildIE007Xml(withRootLevelAttirbutes = xml2001namespace))
        xmlValidationService.validate(xml, ArrivalNotificationXSD) mustBe a[Right[_, _]]

      }
    }

    "must be successful when validating a valid DepartureDeclaration xml" - {

      "with minimal completed fields" in {
        val xml = buildIE015Xml()

        xmlValidationService.validate(XML.loadString(xml), DepartureDeclarationXSD) mustBe a[Right[_, _]]
      }

    }

    "must validate message types successfully" - {

      "with correct Message Type" - {
        val prefixes: List[String] = List("CC", "GB", "XI")

        "for CC015B" in prefixes.foreach {
          v =>
            val xml = buildIE015Xml(withMessageType = s"${v}015B")

            xmlValidationService.validate(XML.loadString(xml), DepartureDeclarationXSD) mustBe Right(XmlSuccessfullyValidated)
        }

        "for CC007A" in prefixes.foreach {
          v =>
            val xml = buildIE007Xml(withMessageType = s"${v}007A")

            xmlValidationService.validate(XML.loadString(xml), ArrivalNotificationXSD) mustBe Right(XmlSuccessfullyValidated)
        }

        "for CC044A" in prefixes.foreach {
          v =>
            val xml = buildIE044Xml(withMessageType = s"${v}044A")

            xmlValidationService.validate(XML.loadString(xml), UnloadingRemarksXSD) mustBe Right(XmlSuccessfullyValidated)
        }

        "for CC014A" in prefixes.foreach {
          v =>
            val xml = buildIE014Xml(withMessageType = s"${v}014A")

            xmlValidationService.validate(XML.loadString(xml), DeclarationCancellationRequestXSD) mustBe Right(XmlSuccessfullyValidated)
        }
      }

      "with a Message Type that doesn't match the message" - {
        val prefixes: List[String] = List("CC", "GB", "XI")
        val values: List[String]   = List("015B", "007A", "014A", "044A")

        def forResult(code: String): List[String] = for {
          p <- prefixes
          v <- values if !v.equalsIgnoreCase(code)
        } yield p + v

        "for CC015B" in forResult("015B").foreach {
          v =>
            val xml = buildIE015Xml(withMessageType = v)

            val expectedMessage =
              s"The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc015b.xsd'. Detailed error below:\ncvc-pattern-valid: Value '$v' is not facet-valid with respect to pattern '(CC|GB|XI)015B' for type 'CC015B_MessageType'."

            xmlValidationService.validate(XML.loadString(xml), DepartureDeclarationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
        }

        "for CC007A" in forResult("007A").foreach {
          v =>
            val xml = buildIE007Xml(withMessageType = v)

            val expectedMessage =
              s"The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc007a.xsd'. Detailed error below:\ncvc-pattern-valid: Value '$v' is not facet-valid with respect to pattern '(CC|GB|XI)007A' for type 'CC007A_MessageType'."

            xmlValidationService.validate(XML.loadString(xml), ArrivalNotificationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
        }

        "for CC044A" in forResult("044A").foreach {
          v =>
            val xml = buildIE044Xml(withMessageType = v)

            val expectedMessage =
              s"The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc044a.xsd'. Detailed error below:\ncvc-pattern-valid: Value '$v' is not facet-valid with respect to pattern '(CC|GB|XI)044A' for type 'CC044A_MessageType'."

            xmlValidationService.validate(XML.loadString(xml), UnloadingRemarksXSD) mustBe Left(FailedToValidateXml(expectedMessage))
        }

        "for CC014A" in forResult("014A").foreach {
          v =>
            val xml = buildIE014Xml(withMessageType = v)

            val expectedMessage =
              s"The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc014a.xsd'. Detailed error below:\ncvc-pattern-valid: Value '$v' is not facet-valid with respect to pattern '(CC|GB|XI)014A' for type 'CC014A_MessageType'."

            xmlValidationService.validate(XML.loadString(xml), DeclarationCancellationRequestXSD) mustBe Left(FailedToValidateXml(expectedMessage))
        }

      }

      "with incorrect Message Type" - {
        val values: List[String] = List("toos", "toolong", "CC000A", "GB015C", "NI007A", "NI015B", "NI014A", "NI044A")

        "for CC015B" in values.foreach {
          v =>
            val xml = buildIE015Xml(withMessageType = v)

            val expectedMessage =
              s"The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc015b.xsd'. Detailed error below:\ncvc-pattern-valid: Value '$v' is not facet-valid with respect to pattern '(CC|GB|XI)015B' for type 'CC015B_MessageType'."

            xmlValidationService.validate(XML.loadString(xml), DepartureDeclarationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
        }

        "for CC007A" in values.foreach {
          v =>
            val xml = buildIE007Xml(withMessageType = v)

            val expectedMessage =
              s"The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc007a.xsd'. Detailed error below:\ncvc-pattern-valid: Value '$v' is not facet-valid with respect to pattern '(CC|GB|XI)007A' for type 'CC007A_MessageType'."

            xmlValidationService.validate(XML.loadString(xml), ArrivalNotificationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
        }

        "for CC044A" in values.foreach {
          v =>
            val xml = buildIE044Xml(withMessageType = v)

            val expectedMessage =
              s"The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc044a.xsd'. Detailed error below:\ncvc-pattern-valid: Value '$v' is not facet-valid with respect to pattern '(CC|GB|XI)044A' for type 'CC044A_MessageType'."

            xmlValidationService.validate(XML.loadString(xml), UnloadingRemarksXSD) mustBe Left(FailedToValidateXml(expectedMessage))
        }

        "for CC014A" in values.foreach {
          v =>
            val xml = buildIE014Xml(withMessageType = v)

            val expectedMessage =
              s"The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc014a.xsd'. Detailed error below:\ncvc-pattern-valid: Value '$v' is not facet-valid with respect to pattern '(CC|GB|XI)014A' for type 'CC014A_MessageType'."

            xmlValidationService.validate(XML.loadString(xml), DeclarationCancellationRequestXSD) mustBe Left(FailedToValidateXml(expectedMessage))
        }

      }
    }

    "must fail when validating an invalid DepartureDeclaration xml" - {

      "with missing mandatory elements" in {
        val xml = <CC015B></CC015B>

        val expectedMessage =
          "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc015b.xsd'. Detailed error below:\ncvc-complex-type.2.4.b: The content of element 'CC015B' is not complete. One of '{SynIdeMES1}' is expected."

        xmlValidationService.validate(xml, DepartureDeclarationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }

      "with invalid fields" in {

        val xml = <CC015B><SynIdeMES1>11111111111111</SynIdeMES1></CC015B>

        val expectedMessage =
          "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc015b.xsd'. Detailed error below:\ncvc-pattern-valid: Value '11111111111111' is not facet-valid with respect to pattern '[a-zA-Z]{4}' for type 'Alpha_4'."

        xmlValidationService.validate(xml, DepartureDeclarationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }

      "with an empty body" in {

        val expectedMessage = "The request cannot be processed as it does not contain a request body."

        xmlValidationService.validate(NodeSeq.Empty, DepartureDeclarationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }
    }

    "must fail when validating an invalid ArrivalNotification xml" - {

      "with missing mandatory elements" in {
        val xml = "<CC007A></CC007A>"

        val expectedMessage =
          "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc007a.xsd'. Detailed error below:\ncvc-complex-type.2.4.b: The content of element 'CC007A' is not complete. One of '{SynIdeMES1}' is expected."

        xmlValidationService.validate(XML.loadString(xml), ArrivalNotificationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
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

        xmlValidationService.validate(XML.loadString(xml), ArrivalNotificationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }

      "with an empty body" in {

        val expectedMessage = "The request cannot be processed as it does not contain a request body."

        xmlValidationService.validate(NodeSeq.Empty, ArrivalNotificationXSD) mustBe Left(FailedToValidateXml(expectedMessage))
      }

      "with an unexpected namespace when the config is set to block unknown namespaces" in {

        val xml = XML.loadString(buildIE007Xml(withRootLevelAttirbutes = xml2001namespace))

        val expectedMessage =
          "The request cannot be processed as it contains a namespace on the root node. Please remove any \"xmlns\" attributes from all nodes."

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
    withMessageType: String = "GB007A",
    withRootLevelAttirbutes: String = ""
  ): String = {

    val enrouteEvent = {
      if (withEnrouteEvent)
        buildEnrouteEvent(withIncident, withContainerTranshipment, withVehicularTranshipment, withSeals)
      else ""
    }

    s"""
       |<CC007A $withRootLevelAttirbutes>
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

  private def buildIE014Xml(withMessageType: String): String =
    s"""
       |<CC014A>
       |    <SynIdeMES1>tval</SynIdeMES1>
       |    <SynVerNumMES2>1</SynVerNumMES2>
       |    <!--Optional:-->
       |    <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>
       |    <MesRecMES6>111111</MesRecMES6>
       |    <!--Optional:-->
       |    <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>
       |    <DatOfPreMES9>20001001</DatOfPreMES9>
       |    <TimOfPreMES10>1111</TimOfPreMES10>
       |    <IntConRefMES11>111111</IntConRefMES11>
       |    <!--Optional:-->
       |    <RecRefMES12>111111</RecRefMES12>
       |    <!--Optional:-->
       |    <RecRefQuaMES13>to</RecRefQuaMES13>
       |    <!--Optional:-->
       |    <AppRefMES14>token</AppRefMES14>
       |    <!--Optional:-->
       |    <PriMES15>t</PriMES15>
       |    <!--Optional:-->
       |    <AckReqMES16>1</AckReqMES16>
       |    <!--Optional:-->
       |    <ComAgrIdMES17>token</ComAgrIdMES17>
       |    <!--Optional:-->
       |    <TesIndMES18>1</TesIndMES18>
       |    <MesIdeMES19>token</MesIdeMES19>
       |    <MesTypMES20>$withMessageType</MesTypMES20>
       |    <!--Optional:-->
       |    <ComAccRefMES21>token</ComAccRefMES21>
       |    <!--Optional:-->
       |    <MesSeqNumMES22>11</MesSeqNumMES22>
       |    <!--Optional:-->
       |    <FirAndLasTraMES23>t</FirAndLasTraMES23>
       |    <HEAHEA>
       |      <DocNumHEA5>default</DocNumHEA5>
       |      <DatOfCanReqHEA147>20001001</DatOfCanReqHEA147>
       |      <CanReaHEA250>default</CanReaHEA250>
       |      <CanReaHEA250LNG>ab</CanReaHEA250LNG>
       |    </HEAHEA>
       |    <TRAPRIPC1>
       |    </TRAPRIPC1>
       |    <CUSOFFDEPEPT>
       |      <RefNumEPT1>default1</RefNumEPT1>
       |    </CUSOFFDEPEPT>
       |  </CC014A>
     """.stripMargin

  private def buildIE044Xml(withMessageType: String): String =
    s"""<CC044A>
       |      <SynIdeMES1>tval</SynIdeMES1>
       |      <SynVerNumMES2>1</SynVerNumMES2>
       |      <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>
       |      <MesRecMES6>111111</MesRecMES6>
       |      <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>
       |      <DatOfPreMES9>20001001</DatOfPreMES9>
       |      <TimOfPreMES10>1111</TimOfPreMES10>
       |      <IntConRefMES11>111111</IntConRefMES11>
       |      <RecRefMES12>111111</RecRefMES12>
       |      <RecRefQuaMES13>to</RecRefQuaMES13>
       |      <AppRefMES14>token</AppRefMES14>
       |      <PriMES15>t</PriMES15>
       |      <AckReqMES16>1</AckReqMES16>
       |      <ComAgrIdMES17>token</ComAgrIdMES17>
       |      <TesIndMES18>1</TesIndMES18>
       |      <MesIdeMES19>token</MesIdeMES19>
       |      <MesTypMES20>$withMessageType</MesTypMES20>
       |      <ComAccRefMES21>token</ComAccRefMES21>
       |      <MesSeqNumMES22>11</MesSeqNumMES22>
       |      <FirAndLasTraMES23>t</FirAndLasTraMES23>
       |      <HEAHEA>
       |        <DocNumHEA5>token</DocNumHEA5>
       |          <IdeOfMeaOfTraAtDHEA78>token</IdeOfMeaOfTraAtDHEA78>
       |          <IdeOfMeaOfTraAtDHEA78LNG>to</IdeOfMeaOfTraAtDHEA78LNG>
       |          <NatOfMeaOfTraAtDHEA80>to</NatOfMeaOfTraAtDHEA80>
       |        <TotNumOfIteHEA305>11</TotNumOfIteHEA305>
       |          <TotNumOfPacHEA306>11</TotNumOfPacHEA306>
       |        <TotGroMasHEA307>1.0</TotGroMasHEA307>
       |      </HEAHEA>
       |      <TRADESTRD>
       |          <NamTRD7>token</NamTRD7>
       |          <StrAndNumTRD22>token</StrAndNumTRD22>
       |          <PosCodTRD23>token</PosCodTRD23>
       |          <CitTRD24>token</CitTRD24>
       |          <CouTRD25>to</CouTRD25>
       |          <NADLNGRD>to</NADLNGRD>
       |          <TINTRD59>token</TINTRD59>
       |      </TRADESTRD>
       |      <CUSOFFPREOFFRES>
       |        <RefNumRES1>tokenval</RefNumRES1>
       |      </CUSOFFPREOFFRES>
       |      <UNLREMREM>
       |          <StaOfTheSeaOKREM19>1</StaOfTheSeaOKREM19>
       |          <UnlRemREM53>token</UnlRemREM53>
       |          <UnlRemREM53LNG>to</UnlRemREM53LNG>
       |        <ConREM65>1</ConREM65>
       |        <UnlComREM66>1</UnlComREM66>
       |        <UnlDatREM67>11010110</UnlDatREM67>
       |      </UNLREMREM>
       |      <RESOFCON534>
       |          <DesTOC2>token</DesTOC2>
       |          <DesTOC2LNG>to</DesTOC2LNG>
       |        <ConInd424>to</ConInd424>
       |          <PoiToTheAttTOC5>token</PoiToTheAttTOC5>
       |          <CorValTOC4>token</CorValTOC4>
       |      </RESOFCON534>
       |      <SEAINFSLI>
       |        <SeaNumSLI2>tval</SeaNumSLI2>
       |        <SEAIDSID>
       |          <SeaIdeSID1>token</SeaIdeSID1>
       |              <SeaIdeSID1LNG>to</SeaIdeSID1LNG>
       |        </SEAIDSID>
       |      </SEAINFSLI>
       |      <GOOITEGDS>
       |        <IteNumGDS7>1</IteNumGDS7>
       |          <ComCodTarCodGDS10>token</ComCodTarCodGDS10>
       |          <GooDesGDS23>token</GooDesGDS23>
       |          <GooDesGDS23LNG>to</GooDesGDS23LNG>
       |          <GroMasGDS46>1.0</GroMasGDS46>
       |          <NetMasGDS48>1.0</NetMasGDS48>
       |        <PRODOCDC2>
       |          <DocTypDC21>tval</DocTypDC21>
       |              <DocRefDC23>token</DocRefDC23>
       |              <DocRefDCLNG>to</DocRefDCLNG>
       |              <ComOfInfDC25>token</ComOfInfDC25>
       |              <ComOfInfDC25LNG>to</ComOfInfDC25LNG>
       |        </PRODOCDC2>
       |        <RESOFCONROC>
       |              <DesROC2>token</DesROC2>
       |              <DesROC2LNG>to</DesROC2LNG>
       |          <ConIndROC1>to</ConIndROC1>
       |              <PoiToTheAttROC51>token</PoiToTheAttROC51>
       |        </RESOFCONROC>
       |        <CONNR2>
       |          <ConNumNR21>token</ConNumNR21>
       |        </CONNR2>
       |        <PACGS2>
       |              <MarNumOfPacGS21>token</MarNumOfPacGS21>
       |              <MarNumOfPacGS21LNG>to</MarNumOfPacGS21LNG>
       |          <KinOfPacGS23>val</KinOfPacGS23>
       |              <NumOfPacGS24>token</NumOfPacGS24>
       |              <NumOfPieGS25>token</NumOfPieGS25>
       |        </PACGS2>
       |        <SGICODSD2>
       |              <SenGooCodSD22>1</SenGooCodSD22>
       |              <SenQuaSD23>1.0</SenQuaSD23>
       |        </SGICODSD2>
       |      </GOOITEGDS>
       |    </CC044A>""".stripMargin

  private def buildIE015Xml(withMessageType: String = "GB015B"): String =
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
