package services

import connectors.WiremockSuite
import models.request.{DepartureDeclarationXSD, XSDFile}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import utils.guaranteeParsing.GuaranteeXmlReaders

class EnsureGuaranteeServiceIntegrationSpec extends AnyFreeSpec with Matchers with WiremockSuite with ScalaFutures with IntegrationPatience with ScalaCheckPropertyChecks{

  "ensureGuarantee" - {

    val inputXML = <CC015B>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesRecMES6>NCTS</MesRecMES6>
      <DatOfPreMES9>20201105</DatOfPreMES9>
      <TimOfPreMES10>1213</TimOfPreMES10>
      <IntConRefMES11>39992585600044</IntConRefMES11>
      <AppRefMES14>NCTS</AppRefMES14>
      <MesIdeMES19>1</MesIdeMES19>
      <MesTypMES20>GB015B</MesTypMES20>
      <HEAHEA>
        <RefNumHEA4>TRATESTDEC12011051213</RefNumHEA4>
        <TypOfDecHEA24>T1</TypOfDecHEA24>
        <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>
        <AutLocOfGooCodHEA41>954131533-GB60DEP</AutLocOfGooCodHEA41>
        <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>
        <TraModAtBorHEA76>3</TraModAtBorHEA76>
        <IdeOfMeaOfTraAtDHEA78>NC15 REG</IdeOfMeaOfTraAtDHEA78>
        <NatOfMeaOfTraAtDHEA80>GB</NatOfMeaOfTraAtDHEA80>
        <NatOfMeaOfTraCroHEA87>GB</NatOfMeaOfTraCroHEA87>
        <ConIndHEA96>0</ConIndHEA96>
        <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>
        <TotNumOfIteHEA305>1</TotNumOfIteHEA305>
        <TotNumOfPacHEA306>10</TotNumOfPacHEA306>
        <TotGroMasHEA307>1000</TotGroMasHEA307>
        <DecDatHEA383>20190912</DecDatHEA383>
        <DecPlaHEA394>Dover</DecPlaHEA394>
      </HEAHEA>
      <TRAPRIPC1>
        <NamPC17>NCTS UK TEST LAB HMCE</NamPC17>
        <StrAndNumPC122>11TH FLOOR, ALEX HOUSE, VICTORIA AV</StrAndNumPC122>
        <PosCodPC123>SS99 1AA</PosCodPC123>
        <CitPC124>SOUTHEND-ON-SEA, ESSEX</CitPC124>
        <CouPC125>GB</CouPC125>
        <TINPC159>GB954131533000</TINPC159>
      </TRAPRIPC1>
      <TRACONCO1>
        <NamCO17>NCTS UK TEST LAB HMCE</NamCO17>
        <StrAndNumCO122>11TH FLOOR, ALEX HOUSE, VICTORIA AV</StrAndNumCO122>
        <PosCodCO123>SS99 1AA</PosCodCO123>
        <CitCO124>SOUTHEND-ON-SEA, ESSEX</CitCO124>
        <CouCO125>GB</CouCO125>
        <TINCO159>GB954131533000</TINCO159>
      </TRACONCO1>
      <TRACONCE1>
        <NamCE17>NCTS UK TEST LAB HMCE</NamCE17>
        <StrAndNumCE122>ITALIAN OFFICE</StrAndNumCE122>
        <PosCodCE123>IT99 1IT</PosCodCE123>
        <CitCE124>MILAN</CitCE124>
        <CouCE125>IT</CouCE125>
        <TINCE159>IT11ITALIANC11</TINCE159>
      </TRACONCE1>
      <CUSOFFDEPEPT>
        <RefNumEPT1>GB000060</RefNumEPT1>
      </CUSOFFDEPEPT>
      <CUSOFFTRARNS>
        <RefNumRNS1>FR001260</RefNumRNS1>
        <ArrTimTRACUS085>202011071213</ArrTimTRACUS085>
      </CUSOFFTRARNS>
      <CUSOFFDESEST>
        <RefNumEST1>IT018100</RefNumEST1>
      </CUSOFFDESEST>
      <CONRESERS>
        <ConResCodERS16>A3</ConResCodERS16>
        <DatLimERS69>20201113</DatLimERS69>
      </CONRESERS>
      <SEAINFSLI>
        <SeaNumSLI2>1</SeaNumSLI2>
        <SEAIDSID>
          <SeaIdeSID1>NCTS001</SeaIdeSID1>
        </SEAIDSID>
      </SEAINFSLI>
      <GUAGUA>
        <GuaTypGUA1>1</GuaTypGUA1>
        <GUAREFREF>
          <GuaRefNumGRNREF1>20GB0000010000GX1</GuaRefNumGRNREF1>
          <AccCodREF6>AC01</AccCodREF6>
        </GUAREFREF>
      </GUAGUA>
      <GOOITEGDS>
        <IteNumGDS7>1</IteNumGDS7>
        <GooDesGDS23>Wheat</GooDesGDS23>
        <GooDesGDS23LNG>EN</GooDesGDS23LNG>
        <GroMasGDS46>1000</GroMasGDS46>
        <NetMasGDS48>950</NetMasGDS48>
        <SPEMENMT2>
          <AddInfMT21>20GB0000010000GX1</AddInfMT21>
          <AddInfCodMT23>CAL</AddInfCodMT23>
        </SPEMENMT2>
        <PACGS2>
          <MarNumOfPacGS21>AB234</MarNumOfPacGS21>
          <KinOfPacGS23>BX</KinOfPacGS23>
          <NumOfPacGS24>10</NumOfPacGS24>
        </PACGS2>
      </GOOITEGDS>
    </CC015B>

    val expectedXML = <CC015B>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesRecMES6>NCTS</MesRecMES6>
      <DatOfPreMES9>20201105</DatOfPreMES9>
      <TimOfPreMES10>1213</TimOfPreMES10>
      <IntConRefMES11>39992585600044</IntConRefMES11>
      <AppRefMES14>NCTS</AppRefMES14>
      <MesIdeMES19>1</MesIdeMES19>
      <MesTypMES20>GB015B</MesTypMES20>
      <HEAHEA>
        <RefNumHEA4>TRATESTDEC12011051213</RefNumHEA4>
        <TypOfDecHEA24>T1</TypOfDecHEA24>
        <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>
        <AutLocOfGooCodHEA41>954131533-GB60DEP</AutLocOfGooCodHEA41>
        <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>
        <TraModAtBorHEA76>3</TraModAtBorHEA76>
        <IdeOfMeaOfTraAtDHEA78>NC15 REG</IdeOfMeaOfTraAtDHEA78>
        <NatOfMeaOfTraAtDHEA80>GB</NatOfMeaOfTraAtDHEA80>
        <NatOfMeaOfTraCroHEA87>GB</NatOfMeaOfTraCroHEA87>
        <ConIndHEA96>0</ConIndHEA96>
        <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>
        <TotNumOfIteHEA305>1</TotNumOfIteHEA305>
        <TotNumOfPacHEA306>10</TotNumOfPacHEA306>
        <TotGroMasHEA307>1000</TotGroMasHEA307>
        <DecDatHEA383>20190912</DecDatHEA383>
        <DecPlaHEA394>Dover</DecPlaHEA394>
      </HEAHEA>
      <TRAPRIPC1>
        <NamPC17>NCTS UK TEST LAB HMCE</NamPC17>
        <StrAndNumPC122>11TH FLOOR, ALEX HOUSE, VICTORIA AV</StrAndNumPC122>
        <PosCodPC123>SS99 1AA</PosCodPC123>
        <CitPC124>SOUTHEND-ON-SEA, ESSEX</CitPC124>
        <CouPC125>GB</CouPC125>
        <TINPC159>GB954131533000</TINPC159>
      </TRAPRIPC1>
      <TRACONCO1>
        <NamCO17>NCTS UK TEST LAB HMCE</NamCO17>
        <StrAndNumCO122>11TH FLOOR, ALEX HOUSE, VICTORIA AV</StrAndNumCO122>
        <PosCodCO123>SS99 1AA</PosCodCO123>
        <CitCO124>SOUTHEND-ON-SEA, ESSEX</CitCO124>
        <CouCO125>GB</CouCO125>
        <TINCO159>GB954131533000</TINCO159>
      </TRACONCO1>
      <TRACONCE1>
        <NamCE17>NCTS UK TEST LAB HMCE</NamCE17>
        <StrAndNumCE122>ITALIAN OFFICE</StrAndNumCE122>
        <PosCodCE123>IT99 1IT</PosCodCE123>
        <CitCE124>MILAN</CitCE124>
        <CouCE125>IT</CouCE125>
        <TINCE159>IT11ITALIANC11</TINCE159>
      </TRACONCE1>
      <CUSOFFDEPEPT>
        <RefNumEPT1>GB000060</RefNumEPT1>
      </CUSOFFDEPEPT>
      <CUSOFFTRARNS>
        <RefNumRNS1>FR001260</RefNumRNS1>
        <ArrTimTRACUS085>202011071213</ArrTimTRACUS085>
      </CUSOFFTRARNS>
      <CUSOFFDESEST>
        <RefNumEST1>IT018100</RefNumEST1>
      </CUSOFFDESEST>
      <CONRESERS>
        <ConResCodERS16>A3</ConResCodERS16>
        <DatLimERS69>20201113</DatLimERS69>
      </CONRESERS>
      <SEAINFSLI>
        <SeaNumSLI2>1</SeaNumSLI2>
        <SEAIDSID>
          <SeaIdeSID1>NCTS001</SeaIdeSID1>
        </SEAIDSID>
      </SEAINFSLI>
      <GUAGUA>
        <GuaTypGUA1>1</GuaTypGUA1>
        <GUAREFREF>
          <GuaRefNumGRNREF1>20GB0000010000GX1</GuaRefNumGRNREF1>
          <AccCodREF6>AC01</AccCodREF6>
        </GUAREFREF>
      </GUAGUA>
      <GOOITEGDS>
        <IteNumGDS7>1</IteNumGDS7>
        <GooDesGDS23>Wheat</GooDesGDS23>
        <GooDesGDS23LNG>EN</GooDesGDS23LNG>
        <GroMasGDS46>1000</GroMasGDS46>
        <NetMasGDS48>950</NetMasGDS48>
        <SPEMENMT2><AddInfMT21>10000.00EUR20GB0000010000GX1</AddInfMT21><AddInfCodMT23>CAL</AddInfCodMT23></SPEMENMT2>
      <PACGS2>
          <MarNumOfPacGS21>AB234</MarNumOfPacGS21>
          <KinOfPacGS23>BX</KinOfPacGS23>
          <NumOfPacGS24>10</NumOfPacGS24>
        </PACGS2>
      </GOOITEGDS>
    </CC015B>

    "must default value correct" in {
      val service = app.injector.instanceOf[EnsureGuaranteeService]

      val result = service.ensureGuarantee(inputXML)

      println(result.right.get.toString().filter(_ > ' '))
      result.right.get.toString().filter(_ > ' ') mustEqual expectedXML.toString().filter(_ > ' ')

    }

    "result must pass standard validation" in {
      val service = app.injector.instanceOf[EnsureGuaranteeService]
      val validator = app.injector.instanceOf[XmlValidationService]

      val result = service.ensureGuarantee(inputXML)

      validator.validate(result.right.get.toString().filter(_ > ' '), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
    }
  }


  override protected def portConfigKey: String = "microservice.services.transit-movement-trader-at-destination.port"

}
