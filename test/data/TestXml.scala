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

package data

import scala.annotation.tailrec
import scala.xml.Elem
import scala.xml.NodeSeq

trait TestXml {

  lazy val CC007A = <CC007A>
    <SynIdeMES1>UNOC</SynIdeMES1>
    <SynVerNumMES2>3</SynVerNumMES2>
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
    <CUSOFFPREOFFRES>
      <RefNumRES1>GB000128</RefNumRES1>
    </CUSOFFPREOFFRES>
  </CC007A>

  lazy val InvalidCC007A = <CC007A>
    <SynIdeMES1>UNOC</SynIdeMES1>
    <SynVerNumMES2>3</SynVerNumMES2>
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
    <CUSOFFPREOFFRES>
      <RefNumRES1>GB000128</RefNumRES1>
    </CUSOFFPREOFFRES>
  </CC007A>

  lazy val CC007AwithMesSenMES3 = <CC007A>
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
    <CUSOFFPREOFFRES>
      <RefNumRES1>GB000128</RefNumRES1>
    </CUSOFFPREOFFRES>
  </CC007A>

  lazy val CC014A = <CC014A>
    <SynIdeMES1>tval</SynIdeMES1>
    <SynVerNumMES2>1</SynVerNumMES2>
    <!--Optional:-->
    <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>
    <MesRecMES6>111111</MesRecMES6>
    <!--Optional:-->
    <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>
    <DatOfPreMES9>20001001</DatOfPreMES9>
    <TimOfPreMES10>1111</TimOfPreMES10>
    <IntConRefMES11>111111</IntConRefMES11>
    <!--Optional:-->
    <RecRefMES12>111111</RecRefMES12>
    <!--Optional:-->
    <RecRefQuaMES13>to</RecRefQuaMES13>
    <!--Optional:-->
    <AppRefMES14>token</AppRefMES14>
    <!--Optional:-->
    <PriMES15>t</PriMES15>
    <!--Optional:-->
    <AckReqMES16>1</AckReqMES16>
    <!--Optional:-->
    <ComAgrIdMES17>token</ComAgrIdMES17>
    <!--Optional:-->
    <TesIndMES18>1</TesIndMES18>
    <MesIdeMES19>token</MesIdeMES19>
    <MesTypMES20>CC014A</MesTypMES20>
    <!--Optional:-->
    <ComAccRefMES21>token</ComAccRefMES21>
    <!--Optional:-->
    <MesSeqNumMES22>11</MesSeqNumMES22>
    <!--Optional:-->
    <FirAndLasTraMES23>t</FirAndLasTraMES23>
    <HEAHEA>
      <DocNumHEA5>default</DocNumHEA5>
      <DatOfCanReqHEA147>20001001</DatOfCanReqHEA147>
      <CanReaHEA250>default</CanReaHEA250>
      <CanReaHEA250LNG>ab</CanReaHEA250LNG>
    </HEAHEA>
    <TRAPRIPC1>
    </TRAPRIPC1>
    <CUSOFFDEPEPT>
      <RefNumEPT1>default1</RefNumEPT1>
    </CUSOFFDEPEPT>
  </CC014A>

  lazy val InvalidCC014A = <CC014A>
    <SynIdeMES1>tval</SynIdeMES1>
    <SynVerNumMES2>1</SynVerNumMES2>
    <!--Optional:-->
    <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>
    <MesRecMES6>111111</MesRecMES6>
    <!--Optional:-->
    <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>
    <DatOfPreMES9>20001001</DatOfPreMES9>
    <TimOfPreMES10>1111</TimOfPreMES10>
    <IntConRefMES11>111111</IntConRefMES11>
    <!--Optional:-->
    <RecRefMES12>111111</RecRefMES12>
    <!--Optional:-->
    <RecRefQuaMES13>to</RecRefQuaMES13>
    <!--Optional:-->
    <AppRefMES14>token</AppRefMES14>
    <!--Optional:-->
    <PriMES15>t</PriMES15>
    <!--Optional:-->
    <AckReqMES16>1</AckReqMES16>
    <!--Optional:-->
    <ComAgrIdMES17>token</ComAgrIdMES17>
    <!--Optional:-->
    <TesIndMES18>1</TesIndMES18>
    <MesIdeMES19>token</MesIdeMES19>
    <MesTypMES20>CC014A</MesTypMES20>
    <!--Optional:-->
    <ComAccRefMES21>token</ComAccRefMES21>
    <!--Optional:-->
    <MesSeqNumMES22>11</MesSeqNumMES22>
    <!--Optional:-->
    <FirAndLasTraMES23>t</FirAndLasTraMES23>
  </CC014A>

  lazy val CC014AwithMesSenMES3 = <CC014A>
    <SynIdeMES1>tval</SynIdeMES1>
    <SynVerNumMES2>1</SynVerNumMES2>
    <MesSenMES3>111111</MesSenMES3>
    <!--Optional:-->
    <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>
    <MesRecMES6>111111</MesRecMES6>
    <!--Optional:-->
    <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>
    <DatOfPreMES9>20001001</DatOfPreMES9>
    <TimOfPreMES10>1111</TimOfPreMES10>
    <IntConRefMES11>111111</IntConRefMES11>
    <!--Optional:-->
    <RecRefMES12>111111</RecRefMES12>
    <!--Optional:-->
    <RecRefQuaMES13>to</RecRefQuaMES13>
    <!--Optional:-->
    <AppRefMES14>token</AppRefMES14>
    <!--Optional:-->
    <PriMES15>t</PriMES15>
    <!--Optional:-->
    <AckReqMES16>1</AckReqMES16>
    <!--Optional:-->
    <ComAgrIdMES17>token</ComAgrIdMES17>
    <!--Optional:-->
    <TesIndMES18>1</TesIndMES18>
    <MesIdeMES19>token</MesIdeMES19>
    <MesTypMES20>CC014A</MesTypMES20>
    <!--Optional:-->
    <ComAccRefMES21>token</ComAccRefMES21>
    <!--Optional:-->
    <MesSeqNumMES22>11</MesSeqNumMES22>
    <!--Optional:-->
    <FirAndLasTraMES23>t</FirAndLasTraMES23>
    <HEAHEA>
      <DocNumHEA5>default</DocNumHEA5>
      <DatOfCanReqHEA147>20001001</DatOfCanReqHEA147>
      <CanReaHEA250>default</CanReaHEA250>
      <CanReaHEA250LNG>ab</CanReaHEA250LNG>
    </HEAHEA>
    <TRAPRIPC1>
    </TRAPRIPC1>
    <CUSOFFDEPEPT>
      <RefNumEPT1>default1</RefNumEPT1>
    </CUSOFFDEPEPT>
  </CC014A>

  lazy val CC044A =
    <CC044A>
      <SynIdeMES1>tval</SynIdeMES1>
      <SynVerNumMES2>1</SynVerNumMES2>
      <!--Optional:-->
      <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>
      <MesRecMES6>111111</MesRecMES6>
      <!--Optional:-->
      <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>
      <DatOfPreMES9>20001001</DatOfPreMES9>
      <TimOfPreMES10>1111</TimOfPreMES10>
      <IntConRefMES11>111111</IntConRefMES11>
      <!--Optional:-->
      <RecRefMES12>111111</RecRefMES12>
      <!--Optional:-->
      <RecRefQuaMES13>to</RecRefQuaMES13>
      <!--Optional:-->
      <AppRefMES14>token</AppRefMES14>
      <!--Optional:-->
      <PriMES15>t</PriMES15>
      <!--Optional:-->
      <AckReqMES16>1</AckReqMES16>
      <!--Optional:-->
      <ComAgrIdMES17>token</ComAgrIdMES17>
      <!--Optional:-->
      <TesIndMES18>1</TesIndMES18>
      <MesIdeMES19>token</MesIdeMES19>
      <MesTypMES20>CC044A</MesTypMES20>
      <!--Optional:-->
      <ComAccRefMES21>token</ComAccRefMES21>
      <!--Optional:-->
      <MesSeqNumMES22>11</MesSeqNumMES22>
      <!--Optional:-->
      <FirAndLasTraMES23>t</FirAndLasTraMES23>
      <HEAHEA>
        <DocNumHEA5>token</DocNumHEA5>
        <!--Optional:-->
        <IdeOfMeaOfTraAtDHEA78>token</IdeOfMeaOfTraAtDHEA78>
        <!--Optional:-->
        <IdeOfMeaOfTraAtDHEA78LNG>to</IdeOfMeaOfTraAtDHEA78LNG>
        <!--Optional:-->
        <NatOfMeaOfTraAtDHEA80>to</NatOfMeaOfTraAtDHEA80>
        <TotNumOfIteHEA305>11</TotNumOfIteHEA305>
        <!--Optional:-->
        <TotNumOfPacHEA306>11</TotNumOfPacHEA306>
        <TotGroMasHEA307>1.0</TotGroMasHEA307>
      </HEAHEA>
      <TRADESTRD>
        <!--Optional:-->
        <NamTRD7>token</NamTRD7>
        <!--Optional:-->
        <StrAndNumTRD22>token</StrAndNumTRD22>
        <!--Optional:-->
        <PosCodTRD23>token</PosCodTRD23>
        <!--Optional:-->
        <CitTRD24>token</CitTRD24>
        <!--Optional:-->
        <CouTRD25>to</CouTRD25>
        <!--Optional:-->
        <NADLNGRD>to</NADLNGRD>
        <!--Optional:-->
        <TINTRD59>token</TINTRD59>
      </TRADESTRD>
      <CUSOFFPREOFFRES>
        <RefNumRES1>tokenval</RefNumRES1>
      </CUSOFFPREOFFRES>
      <UNLREMREM>
        <!--Optional:-->
        <StaOfTheSeaOKREM19>1</StaOfTheSeaOKREM19>
        <!--Optional:-->
        <UnlRemREM53>token</UnlRemREM53>
        <!--Optional:-->
        <UnlRemREM53LNG>to</UnlRemREM53LNG>
        <ConREM65>1</ConREM65>
        <UnlComREM66>1</UnlComREM66>
        <UnlDatREM67>11010110</UnlDatREM67>
      </UNLREMREM>
      <!--0 to 9 repetitions:-->
      <RESOFCON534>
        <!--Optional:-->
        <DesTOC2>token</DesTOC2>
        <!--Optional:-->
        <DesTOC2LNG>to</DesTOC2LNG>
        <ConInd424>to</ConInd424>
        <!--Optional:-->
        <PoiToTheAttTOC5>token</PoiToTheAttTOC5>
        <!--Optional:-->
        <CorValTOC4>token</CorValTOC4>
      </RESOFCON534>
      <!--Optional:-->
      <SEAINFSLI>
        <SeaNumSLI2>tval</SeaNumSLI2>
        <!--0 to 9999 repetitions:-->
        <SEAIDSID>
          <SeaIdeSID1>token</SeaIdeSID1>
          <!--Optional:-->
          <SeaIdeSID1LNG>to</SeaIdeSID1LNG>
        </SEAIDSID>
      </SEAINFSLI>
      <!--0 to 9999 repetitions:-->
      <GOOITEGDS>
        <IteNumGDS7>1</IteNumGDS7>
        <!--Optional:-->
        <ComCodTarCodGDS10>token</ComCodTarCodGDS10>
        <!--Optional:-->
        <GooDesGDS23>token</GooDesGDS23>
        <!--Optional:-->
        <GooDesGDS23LNG>to</GooDesGDS23LNG>
        <!--Optional:-->
        <GroMasGDS46>1.0</GroMasGDS46>
        <!--Optional:-->
        <NetMasGDS48>1.0</NetMasGDS48>
        <!--0 to 99 repetitions:-->
        <PRODOCDC2>
          <DocTypDC21>tval</DocTypDC21>
          <!--Optional:-->
          <DocRefDC23>token</DocRefDC23>
          <!--Optional:-->
          <DocRefDCLNG>to</DocRefDCLNG>
          <!--Optional:-->
          <ComOfInfDC25>token</ComOfInfDC25>
          <!--Optional:-->
          <ComOfInfDC25LNG>to</ComOfInfDC25LNG>
        </PRODOCDC2>
        <!--0 to 199 repetitions:-->
        <RESOFCONROC>
          <!--Optional:-->
          <DesROC2>token</DesROC2>
          <!--Optional:-->
          <DesROC2LNG>to</DesROC2LNG>
          <ConIndROC1>to</ConIndROC1>
          <!--Optional:-->
          <PoiToTheAttROC51>token</PoiToTheAttROC51>
        </RESOFCONROC>
        <!--0 to 99 repetitions:-->
        <CONNR2>
          <ConNumNR21>token</ConNumNR21>
        </CONNR2>
        <!--0 to 99 repetitions:-->
        <PACGS2>
          <!--Optional:-->
          <MarNumOfPacGS21>token</MarNumOfPacGS21>
          <!--Optional:-->
          <MarNumOfPacGS21LNG>to</MarNumOfPacGS21LNG>
          <KinOfPacGS23>val</KinOfPacGS23>
          <!--Optional:-->
          <NumOfPacGS24>token</NumOfPacGS24>
          <!--Optional:-->
          <NumOfPieGS25>token</NumOfPieGS25>
        </PACGS2>
        <!--0 to 9 repetitions:-->
        <SGICODSD2>
          <!--Optional:-->
          <SenGooCodSD22>1</SenGooCodSD22>
          <!--Optional:-->
          <SenQuaSD23>1.0</SenQuaSD23>
        </SGICODSD2>
      </GOOITEGDS>
    </CC044A>

  lazy val CC044AWithMultipleGoodsItems =
    <CC044A>
      <SynIdeMES1>tval</SynIdeMES1>
      <SynVerNumMES2>1</SynVerNumMES2>
      <!--Optional:-->
      <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>
      <MesRecMES6>111111</MesRecMES6>
      <!--Optional:-->
      <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>
      <DatOfPreMES9>20001001</DatOfPreMES9>
      <TimOfPreMES10>1111</TimOfPreMES10>
      <IntConRefMES11>111111</IntConRefMES11>
      <!--Optional:-->
      <RecRefMES12>111111</RecRefMES12>
      <!--Optional:-->
      <RecRefQuaMES13>to</RecRefQuaMES13>
      <!--Optional:-->
      <AppRefMES14>token</AppRefMES14>
      <!--Optional:-->
      <PriMES15>t</PriMES15>
      <!--Optional:-->
      <AckReqMES16>1</AckReqMES16>
      <!--Optional:-->
      <ComAgrIdMES17>token</ComAgrIdMES17>
      <!--Optional:-->
      <TesIndMES18>1</TesIndMES18>
      <MesIdeMES19>token</MesIdeMES19>
      <MesTypMES20>CC044A</MesTypMES20>
      <!--Optional:-->
      <ComAccRefMES21>token</ComAccRefMES21>
      <!--Optional:-->
      <MesSeqNumMES22>11</MesSeqNumMES22>
      <!--Optional:-->
      <FirAndLasTraMES23>t</FirAndLasTraMES23>
      <HEAHEA>
        <DocNumHEA5>token</DocNumHEA5>
        <!--Optional:-->
        <IdeOfMeaOfTraAtDHEA78>token</IdeOfMeaOfTraAtDHEA78>
        <!--Optional:-->
        <IdeOfMeaOfTraAtDHEA78LNG>to</IdeOfMeaOfTraAtDHEA78LNG>
        <!--Optional:-->
        <NatOfMeaOfTraAtDHEA80>to</NatOfMeaOfTraAtDHEA80>
        <TotNumOfIteHEA305>11</TotNumOfIteHEA305>
        <!--Optional:-->
        <TotNumOfPacHEA306>11</TotNumOfPacHEA306>
        <TotGroMasHEA307>1.0</TotGroMasHEA307>
      </HEAHEA>
      <TRADESTRD>
        <!--Optional:-->
        <NamTRD7>token</NamTRD7>
        <!--Optional:-->
        <StrAndNumTRD22>token</StrAndNumTRD22>
        <!--Optional:-->
        <PosCodTRD23>token</PosCodTRD23>
        <!--Optional:-->
        <CitTRD24>token</CitTRD24>
        <!--Optional:-->
        <CouTRD25>to</CouTRD25>
        <!--Optional:-->
        <NADLNGRD>to</NADLNGRD>
        <!--Optional:-->
        <TINTRD59>token</TINTRD59>
      </TRADESTRD>
      <CUSOFFPREOFFRES>
        <RefNumRES1>tokenval</RefNumRES1>
      </CUSOFFPREOFFRES>
      <UNLREMREM>
        <!--Optional:-->
        <StaOfTheSeaOKREM19>1</StaOfTheSeaOKREM19>
        <!--Optional:-->
        <UnlRemREM53>token</UnlRemREM53>
        <!--Optional:-->
        <UnlRemREM53LNG>to</UnlRemREM53LNG>
        <ConREM65>1</ConREM65>
        <UnlComREM66>1</UnlComREM66>
        <UnlDatREM67>11010110</UnlDatREM67>
      </UNLREMREM>
      <!--0 to 9 repetitions:-->
      <RESOFCON534>
        <!--Optional:-->
        <DesTOC2>token</DesTOC2>
        <!--Optional:-->
        <DesTOC2LNG>to</DesTOC2LNG>
        <ConInd424>to</ConInd424>
        <!--Optional:-->
        <PoiToTheAttTOC5>token</PoiToTheAttTOC5>
        <!--Optional:-->
        <CorValTOC4>token</CorValTOC4>
      </RESOFCON534>
      <!--Optional:-->
      <SEAINFSLI>
        <SeaNumSLI2>tval</SeaNumSLI2>
        <!--0 to 9999 repetitions:-->
        <SEAIDSID>
          <SeaIdeSID1>token</SeaIdeSID1>
          <!--Optional:-->
          <SeaIdeSID1LNG>to</SeaIdeSID1LNG>
        </SEAIDSID>
      </SEAINFSLI>
      <!--0 to 9999 repetitions:-->
      <GOOITEGDS>
        <IteNumGDS7>1</IteNumGDS7>
        <!--Optional:-->
        <ComCodTarCodGDS10>token</ComCodTarCodGDS10>
        <!--Optional:-->
        <GooDesGDS23>token</GooDesGDS23>
        <!--Optional:-->
        <GooDesGDS23LNG>to</GooDesGDS23LNG>
        <!--Optional:-->
        <GroMasGDS46>1.0</GroMasGDS46>
        <!--Optional:-->
        <NetMasGDS48>1.0</NetMasGDS48>
        <!--0 to 99 repetitions:-->
        <PRODOCDC2>
          <DocTypDC21>tval</DocTypDC21>
          <!--Optional:-->
          <DocRefDC23>token</DocRefDC23>
          <!--Optional:-->
          <DocRefDCLNG>to</DocRefDCLNG>
          <!--Optional:-->
          <ComOfInfDC25>token</ComOfInfDC25>
          <!--Optional:-->
          <ComOfInfDC25LNG>to</ComOfInfDC25LNG>
        </PRODOCDC2>
        <!--0 to 199 repetitions:-->
        <RESOFCONROC>
          <!--Optional:-->
          <DesROC2>token</DesROC2>
          <!--Optional:-->
          <DesROC2LNG>to</DesROC2LNG>
          <ConIndROC1>to</ConIndROC1>
          <!--Optional:-->
          <PoiToTheAttROC51>token</PoiToTheAttROC51>
        </RESOFCONROC>
        <!--0 to 99 repetitions:-->
        <CONNR2>
          <ConNumNR21>token</ConNumNR21>
        </CONNR2>
        <!--0 to 99 repetitions:-->
        <PACGS2>
          <!--Optional:-->
          <MarNumOfPacGS21>token</MarNumOfPacGS21>
          <!--Optional:-->
          <MarNumOfPacGS21LNG>to</MarNumOfPacGS21LNG>
          <KinOfPacGS23>val</KinOfPacGS23>
          <!--Optional:-->
          <NumOfPacGS24>token</NumOfPacGS24>
          <!--Optional:-->
          <NumOfPieGS25>token</NumOfPieGS25>
        </PACGS2>
        <!--0 to 9 repetitions:-->
        <SGICODSD2>
          <!--Optional:-->
          <SenGooCodSD22>1</SenGooCodSD22>
          <!--Optional:-->
          <SenQuaSD23>1.0</SenQuaSD23>
        </SGICODSD2>
      </GOOITEGDS>
      <GOOITEGDS>
        <IteNumGDS7>2</IteNumGDS7>
        <!--Optional:-->
        <ComCodTarCodGDS10>token</ComCodTarCodGDS10>
        <!--Optional:-->
        <GooDesGDS23>token</GooDesGDS23>
        <!--Optional:-->
        <GooDesGDS23LNG>to</GooDesGDS23LNG>
        <!--Optional:-->
        <GroMasGDS46>1.0</GroMasGDS46>
        <!--Optional:-->
        <NetMasGDS48>1.0</NetMasGDS48>
        <!--0 to 99 repetitions:-->
        <PRODOCDC2>
          <DocTypDC21>tval</DocTypDC21>
          <!--Optional:-->
          <DocRefDC23>token</DocRefDC23>
          <!--Optional:-->
          <DocRefDCLNG>to</DocRefDCLNG>
          <!--Optional:-->
          <ComOfInfDC25>token</ComOfInfDC25>
          <!--Optional:-->
          <ComOfInfDC25LNG>to</ComOfInfDC25LNG>
        </PRODOCDC2>
        <PRODOCDC2>
          <DocTypDC21>tval</DocTypDC21>
          <!--Optional:-->
          <DocRefDC23>token</DocRefDC23>
          <!--Optional:-->
          <DocRefDCLNG>to</DocRefDCLNG>
          <!--Optional:-->
          <ComOfInfDC25>token</ComOfInfDC25>
          <!--Optional:-->
          <ComOfInfDC25LNG>to</ComOfInfDC25LNG>
        </PRODOCDC2>
        <!--0 to 199 repetitions:-->
        <RESOFCONROC>
          <!--Optional:-->
          <DesROC2>token</DesROC2>
          <!--Optional:-->
          <DesROC2LNG>to</DesROC2LNG>
          <ConIndROC1>to</ConIndROC1>
          <!--Optional:-->
          <PoiToTheAttROC51>token</PoiToTheAttROC51>
        </RESOFCONROC>
        <!--0 to 99 repetitions:-->
        <CONNR2>
          <ConNumNR21>token</ConNumNR21>
        </CONNR2>
        <!--0 to 99 repetitions:-->
        <PACGS2>
          <!--Optional:-->
          <MarNumOfPacGS21>token</MarNumOfPacGS21>
          <!--Optional:-->
          <MarNumOfPacGS21LNG>to</MarNumOfPacGS21LNG>
          <KinOfPacGS23>val</KinOfPacGS23>
          <!--Optional:-->
          <NumOfPacGS24>token</NumOfPacGS24>
          <!--Optional:-->
          <NumOfPieGS25>token</NumOfPieGS25>
        </PACGS2>
        <!--0 to 9 repetitions:-->
        <SGICODSD2>
          <!--Optional:-->
          <SenGooCodSD22>1</SenGooCodSD22>
          <!--Optional:-->
          <SenQuaSD23>1.0</SenQuaSD23>
        </SGICODSD2>
      </GOOITEGDS>
    </CC044A>

  lazy val CC044AwithMesSenMES3 =
    <CC044A>
      <SynIdeMES1>tval</SynIdeMES1>
      <SynVerNumMES2>1</SynVerNumMES2>
      <MesSenMES3>111111</MesSenMES3>
      <!--Optional:-->
      <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>
      <MesRecMES6>111111</MesRecMES6>
      <!--Optional:-->
      <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>
      <DatOfPreMES9>20001001</DatOfPreMES9>
      <TimOfPreMES10>1111</TimOfPreMES10>
      <IntConRefMES11>111111</IntConRefMES11>
      <!--Optional:-->
      <RecRefMES12>111111</RecRefMES12>
      <!--Optional:-->
      <RecRefQuaMES13>to</RecRefQuaMES13>
      <!--Optional:-->
      <AppRefMES14>token</AppRefMES14>
      <!--Optional:-->
      <PriMES15>t</PriMES15>
      <!--Optional:-->
      <AckReqMES16>1</AckReqMES16>
      <!--Optional:-->
      <ComAgrIdMES17>token</ComAgrIdMES17>
      <!--Optional:-->
      <TesIndMES18>1</TesIndMES18>
      <MesIdeMES19>token</MesIdeMES19>
      <MesTypMES20>CC044A</MesTypMES20>
      <!--Optional:-->
      <ComAccRefMES21>token</ComAccRefMES21>
      <!--Optional:-->
      <MesSeqNumMES22>11</MesSeqNumMES22>
      <!--Optional:-->
      <FirAndLasTraMES23>t</FirAndLasTraMES23>
      <HEAHEA>
        <DocNumHEA5>token</DocNumHEA5>
        <!--Optional:-->
        <IdeOfMeaOfTraAtDHEA78>token</IdeOfMeaOfTraAtDHEA78>
        <!--Optional:-->
        <IdeOfMeaOfTraAtDHEA78LNG>to</IdeOfMeaOfTraAtDHEA78LNG>
        <!--Optional:-->
        <NatOfMeaOfTraAtDHEA80>to</NatOfMeaOfTraAtDHEA80>
        <TotNumOfIteHEA305>11</TotNumOfIteHEA305>
        <!--Optional:-->
        <TotNumOfPacHEA306>11</TotNumOfPacHEA306>
        <TotGroMasHEA307>1.0</TotGroMasHEA307>
      </HEAHEA>
      <TRADESTRD>
        <!--Optional:-->
        <NamTRD7>token</NamTRD7>
        <!--Optional:-->
        <StrAndNumTRD22>token</StrAndNumTRD22>
        <!--Optional:-->
        <PosCodTRD23>token</PosCodTRD23>
        <!--Optional:-->
        <CitTRD24>token</CitTRD24>
        <!--Optional:-->
        <CouTRD25>to</CouTRD25>
        <!--Optional:-->
        <NADLNGRD>to</NADLNGRD>
        <!--Optional:-->
        <TINTRD59>token</TINTRD59>
      </TRADESTRD>
      <CUSOFFPREOFFRES>
        <RefNumRES1>tokenval</RefNumRES1>
      </CUSOFFPREOFFRES>
      <UNLREMREM>
        <!--Optional:-->
        <StaOfTheSeaOKREM19>1</StaOfTheSeaOKREM19>
        <!--Optional:-->
        <UnlRemREM53>token</UnlRemREM53>
        <!--Optional:-->
        <UnlRemREM53LNG>to</UnlRemREM53LNG>
        <ConREM65>1</ConREM65>
        <UnlComREM66>1</UnlComREM66>
        <UnlDatREM67>11010110</UnlDatREM67>
      </UNLREMREM>
      <!--0 to 9 repetitions:-->
      <RESOFCON534>
        <!--Optional:-->
        <DesTOC2>token</DesTOC2>
        <!--Optional:-->
        <DesTOC2LNG>to</DesTOC2LNG>
        <ConInd424>to</ConInd424>
        <!--Optional:-->
        <PoiToTheAttTOC5>token</PoiToTheAttTOC5>
        <!--Optional:-->
        <CorValTOC4>token</CorValTOC4>
      </RESOFCON534>
      <!--Optional:-->
      <SEAINFSLI>
        <SeaNumSLI2>tval</SeaNumSLI2>
        <!--0 to 9999 repetitions:-->
        <SEAIDSID>
          <SeaIdeSID1>token</SeaIdeSID1>
          <!--Optional:-->
          <SeaIdeSID1LNG>to</SeaIdeSID1LNG>
        </SEAIDSID>
      </SEAINFSLI>
      <!--0 to 9999 repetitions:-->
      <GOOITEGDS>
        <IteNumGDS7>1</IteNumGDS7>
        <!--Optional:-->
        <ComCodTarCodGDS10>token</ComCodTarCodGDS10>
        <!--Optional:-->
        <GooDesGDS23>token</GooDesGDS23>
        <!--Optional:-->
        <GooDesGDS23LNG>to</GooDesGDS23LNG>
        <!--Optional:-->
        <GroMasGDS46>1.0</GroMasGDS46>
        <!--Optional:-->
        <NetMasGDS48>1.0</NetMasGDS48>
        <!--0 to 99 repetitions:-->
        <PRODOCDC2>
          <DocTypDC21>tval</DocTypDC21>
          <!--Optional:-->
          <DocRefDC23>token</DocRefDC23>
          <!--Optional:-->
          <DocRefDCLNG>to</DocRefDCLNG>
          <!--Optional:-->
          <ComOfInfDC25>token</ComOfInfDC25>
          <!--Optional:-->
          <ComOfInfDC25LNG>to</ComOfInfDC25LNG>
        </PRODOCDC2>
        <!--0 to 199 repetitions:-->
        <RESOFCONROC>
          <!--Optional:-->
          <DesROC2>token</DesROC2>
          <!--Optional:-->
          <DesROC2LNG>to</DesROC2LNG>
          <ConIndROC1>to</ConIndROC1>
          <!--Optional:-->
          <PoiToTheAttROC51>token</PoiToTheAttROC51>
        </RESOFCONROC>
        <!--0 to 99 repetitions:-->
        <CONNR2>
          <ConNumNR21>token</ConNumNR21>
        </CONNR2>
        <!--0 to 99 repetitions:-->
        <PACGS2>
          <!--Optional:-->
          <MarNumOfPacGS21>token</MarNumOfPacGS21>
          <!--Optional:-->
          <MarNumOfPacGS21LNG>to</MarNumOfPacGS21LNG>
          <KinOfPacGS23>val</KinOfPacGS23>
          <!--Optional:-->
          <NumOfPacGS24>token</NumOfPacGS24>
          <!--Optional:-->
          <NumOfPieGS25>token</NumOfPieGS25>
        </PACGS2>
        <!--0 to 9 repetitions:-->
        <SGICODSD2>
          <!--Optional:-->
          <SenGooCodSD22>1</SenGooCodSD22>
          <!--Optional:-->
          <SenQuaSD23>1.0</SenQuaSD23>
        </SGICODSD2>
      </GOOITEGDS>
    </CC044A>

  lazy val InvalidCC044A =
    <CC044A>
      <SynIdeMES1>tval</SynIdeMES1>
      <SynVerNumMES2>1</SynVerNumMES2>
      <!--Optional:-->
      <SenIdeCodQuaMES4>1111</SenIdeCodQuaMES4>
      <MesRecMES6>111111</MesRecMES6>
      <!--Optional:-->
      <RecIdeCodQuaMES7>1111</RecIdeCodQuaMES7>
      <DatOfPreMES9>111111</DatOfPreMES9>
      <TimOfPreMES10>1111</TimOfPreMES10>
      <IntConRefMES11>111111</IntConRefMES11>
      <!--Optional:-->
      <RecRefMES12>111111</RecRefMES12>
      <!--Optional:-->
      <RecRefQuaMES13>to</RecRefQuaMES13>
      <!--Optional:-->
      <AppRefMES14>token</AppRefMES14>
      <!--Optional:-->
      <PriMES15>t</PriMES15>
      <!--Optional:-->
      <AckReqMES16>1</AckReqMES16>
      <!--Optional:-->
      <ComAgrIdMES17>token</ComAgrIdMES17>
      <!--Optional:-->
      <TesIndMES18>1</TesIndMES18>
      <MesIdeMES19>token</MesIdeMES19>
      <MesTypMES20>CC044A</MesTypMES20>
      <!--Optional:-->
      <ComAccRefMES21>token</ComAccRefMES21>
      <!--Optional:-->
      <MesSeqNumMES22>11</MesSeqNumMES22>
      <!--Optional:-->
      <FirAndLasTraMES23>t</FirAndLasTraMES23>
      <HEAHEA>
        <DocNumHEA5>token</DocNumHEA5>
        <!--Optional:-->
        <IdeOfMeaOfTraAtDHEA78>token</IdeOfMeaOfTraAtDHEA78>
        <!--Optional:-->
        <IdeOfMeaOfTraAtDHEA78LNG>to</IdeOfMeaOfTraAtDHEA78LNG>
        <!--Optional:-->
        <NatOfMeaOfTraAtDHEA80>to</NatOfMeaOfTraAtDHEA80>
        <TotNumOfIteHEA305>11</TotNumOfIteHEA305>
        <!--Optional:-->
        <TotNumOfPacHEA306>11</TotNumOfPacHEA306>
        <TotGroMasHEA307>1.0</TotGroMasHEA307>
      </HEAHEA>
      <TRADESTRD>
        <!--Optional:-->
        <NamTRD7>token</NamTRD7>
        <!--Optional:-->
        <StrAndNumTRD22>token</StrAndNumTRD22>
        <!--Optional:-->
        <PosCodTRD23>token</PosCodTRD23>
        <!--Optional:-->
        <CitTRD24>token</CitTRD24>
        <!--Optional:-->
        <CouTRD25>to</CouTRD25>
        <!--Optional:-->
        <NADLNGRD>to</NADLNGRD>
        <!--Optional:-->
        <TINTRD59>token</TINTRD59>
      </TRADESTRD>
      <CUSOFFPREOFFRES>
        <RefNumRES1>tokenval</RefNumRES1>
      </CUSOFFPREOFFRES>
      <!--0 to 9 repetitions:-->
      <RESOFCON534>
        <!--Optional:-->
        <DesTOC2>token</DesTOC2>
        <!--Optional:-->
        <DesTOC2LNG>to</DesTOC2LNG>
        <ConInd424>to</ConInd424>
        <!--Optional:-->
        <PoiToTheAttTOC5>token</PoiToTheAttTOC5>
        <!--Optional:-->
        <CorValTOC4>token</CorValTOC4>
      </RESOFCON534>
      <!--Optional:-->
      <SEAINFSLI>
        <SeaNumSLI2>tval</SeaNumSLI2>
        <!--0 to 9999 repetitions:-->
        <SEAIDSID>
          <SeaIdeSID1>token</SeaIdeSID1>
          <!--Optional:-->
          <SeaIdeSID1LNG>to</SeaIdeSID1LNG>
        </SEAIDSID>
      </SEAINFSLI>
      <!--0 to 9999 repetitions:-->
      <GOOITEGDS>
        <IteNumGDS7>1</IteNumGDS7>
        <!--Optional:-->
        <ComCodTarCodGDS10>token</ComCodTarCodGDS10>
        <!--Optional:-->
        <GooDesGDS23>token</GooDesGDS23>
        <!--Optional:-->
        <GooDesGDS23LNG>to</GooDesGDS23LNG>
        <!--Optional:-->
        <GroMasGDS46>1.0</GroMasGDS46>
        <!--Optional:-->
        <NetMasGDS48>1.0</NetMasGDS48>
        <!--0 to 99 repetitions:-->
        <PRODOCDC2>
          <DocTypDC21>tval</DocTypDC21>
          <!--Optional:-->
          <DocRefDC23>token</DocRefDC23>
          <!--Optional:-->
          <DocRefDCLNG>to</DocRefDCLNG>
          <!--Optional:-->
          <ComOfInfDC25>token</ComOfInfDC25>
          <!--Optional:-->
          <ComOfInfDC25LNG>to</ComOfInfDC25LNG>
        </PRODOCDC2>
        <!--0 to 199 repetitions:-->
        <RESOFCONROC>
          <!--Optional:-->
          <DesROC2>token</DesROC2>
          <!--Optional:-->
          <DesROC2LNG>to</DesROC2LNG>
          <ConIndROC1>to</ConIndROC1>
          <!--Optional:-->
          <PoiToTheAttROC51>token</PoiToTheAttROC51>
        </RESOFCONROC>
        <!--0 to 99 repetitions:-->
        <CONNR2>
          <ConNumNR21>token</ConNumNR21>
        </CONNR2>
        <!--0 to 99 repetitions:-->
        <PACGS2>
          <!--Optional:-->
          <MarNumOfPacGS21>token</MarNumOfPacGS21>
          <!--Optional:-->
          <MarNumOfPacGS21LNG>to</MarNumOfPacGS21LNG>
          <KinOfPacGS23>val</KinOfPacGS23>
          <!--Optional:-->
          <NumOfPacGS24>token</NumOfPacGS24>
          <!--Optional:-->
          <NumOfPieGS25>token</NumOfPieGS25>
        </PACGS2>
        <!--0 to 9 repetitions:-->
        <SGICODSD2>
          <!--Optional:-->
          <SenGooCodSD22>1</SenGooCodSD22>
          <!--Optional:-->
          <SenQuaSD23>1.0</SenQuaSD23>
        </SGICODSD2>
      </GOOITEGDS>
    </CC044A>

  lazy val CC015B = <CC015B>
    <SynIdeMES1>UNOC</SynIdeMES1>
    <SynVerNumMES2>3</SynVerNumMES2>
    <MesRecMES6>NCTS</MesRecMES6>
    <DatOfPreMES9>20190912</DatOfPreMES9>
    <TimOfPreMES10>1222</TimOfPreMES10>
    <IntConRefMES11>WE190912102530</IntConRefMES11>
    <AppRefMES14>NCTS</AppRefMES14>
    <TesIndMES18>0</TesIndMES18>
    <MesIdeMES19>1</MesIdeMES19>
    <MesTypMES20>GB015B</MesTypMES20>
    <HEAHEA>
      <RefNumHEA4>01CTC201909121215</RefNumHEA4>
      <TypOfDecHEA24>T2</TypOfDecHEA24>
      <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>
      <AgrLocOfGooCodHEA38>default</AgrLocOfGooCodHEA38>
      <AgrLocOfGooHEA39>default</AgrLocOfGooHEA39>
      <AgrLocOfGooHEA39LNG>EN</AgrLocOfGooHEA39LNG>
      <AutLocOfGooCodHEA41>default</AutLocOfGooCodHEA41>
      <PlaOfLoaCodHEA46>DOVER</PlaOfLoaCodHEA46>
      <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>
      <CusSubPlaHEA66>default</CusSubPlaHEA66>
      <InlTraModHEA75>20</InlTraModHEA75>
      <IdeOfMeaOfTraAtDHEA78>EU_EXIT</IdeOfMeaOfTraAtDHEA78>
      <IdeOfMeaOfTraAtDHEA78LNG>EN</IdeOfMeaOfTraAtDHEA78LNG>
      <IdeOfMeaOfTraCroHEA85>EU_EXIT</IdeOfMeaOfTraCroHEA85>
      <IdeOfMeaOfTraCroHEA85LNG>EN</IdeOfMeaOfTraCroHEA85LNG>
      <ConIndHEA96>0</ConIndHEA96>
      <DiaLanIndAtDepHEA254>EN</DiaLanIndAtDepHEA254>
      <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>
      <TotNumOfIteHEA305>1</TotNumOfIteHEA305>
      <TotNumOfPacHEA306>1</TotNumOfPacHEA306>
      <TotGroMasHEA307>1000</TotGroMasHEA307>
      <DecDatHEA383>20190912</DecDatHEA383>
      <DecPlaHEA394>DOVER</DecPlaHEA394>
      <DecPlaHEA394LNG>EN</DecPlaHEA394LNG>
    </HEAHEA>
    <TRAPRIPC1>
      <NamPC17>CITY WATCH SHIPPING</NamPC17>
      <StrAndNumPC122>125 Psuedopolis Yard</StrAndNumPC122>
      <PosCodPC123>SS99 1AA</PosCodPC123>
      <CitPC124>Ank-Morpork</CitPC124>
      <CouPC125>GB</CouPC125>
      <NADLNGPC>EN</NADLNGPC>
      <TINPC159>GB652420267000</TINPC159>
    </TRAPRIPC1>
    <TRACONCO1>
      <NamCO17>QUIRM ENGINEERING</NamCO17>
      <StrAndNumCO122>125 Psuedopolis Yard</StrAndNumCO122>
      <PosCodCO123>SS99 1AA</PosCodCO123>
      <CitCO124>Ank-Morpork</CitCO124>
      <CouCO125>GB</CouCO125>
      <TINCO159>GB602070107000</TINCO159>
    </TRACONCO1>
    <TRACONCE1>
      <NamCE17>DROFL POTTERY</NamCE17>
      <StrAndNumCE122>125 Psuedopolis Yard</StrAndNumCE122>
      <PosCodCE123>SS99 1AA</PosCodCE123>
      <CitCE124>Ank-Morpork</CitCE124>
      <CouCE125>GB</CouCE125>
      <NADLNGCE>EN</NADLNGCE>
      <TINCE159>GB658120050000</TINCE159>
    </TRACONCE1>
    <CUSOFFDEPEPT>
      <RefNumEPT1>GB000060</RefNumEPT1>
    </CUSOFFDEPEPT>
    <CUSOFFTRARNS>
      <RefNumRNS1>FR001260</RefNumRNS1>
      <ArrTimTRACUS085>201909160100</ArrTimTRACUS085>
    </CUSOFFTRARNS>
    <CUSOFFDESEST>
      <RefNumEST1>IT021100</RefNumEST1>
    </CUSOFFDESEST>
    <SEAINFSLI>
      <SeaNumSLI2>1</SeaNumSLI2>
      <SEAIDSID>
        <SeaIdeSID1>Seal001</SeaIdeSID1>
        <SeaIdeSID1LNG>EN</SeaIdeSID1LNG>
      </SEAIDSID>
    </SEAINFSLI>
    <GUAGUA>
      <GuaTypGUA1>3</GuaTypGUA1>
      <GUAREFREF>
        <GuaRefNumGRNREF1>default</GuaRefNumGRNREF1>
        <OthGuaRefREF4>EU_EXIT</OthGuaRefREF4>
        <AccCodREF6>test</AccCodREF6>
      </GUAREFREF>
    </GUAGUA>
    <GOOITEGDS>
      <IteNumGDS7>1</IteNumGDS7>
      <ComCodTarCodGDS10>default</ComCodTarCodGDS10>
      <DecTypGDS15>default</DecTypGDS15>
      <GooDesGDS23>Flowers</GooDesGDS23>
      <GooDesGDS23LNG>EN</GooDesGDS23LNG>
      <GroMasGDS46>1000</GroMasGDS46>
      <NetMasGDS48>999</NetMasGDS48>
      <CouOfDesGDS59>ex</CouOfDesGDS59>
      <PREADMREFAR2>
        <PreDocTypAR21>T2</PreDocTypAR21>
        <PreDocRefAR26>EU_EXIT-T2</PreDocRefAR26>
        <PreDocRefLNG>EN</PreDocRefLNG>
        <ComOfInfAR29>default</ComOfInfAR29>
        <ComOfInfAR29LNG>EN</ComOfInfAR29LNG>
      </PREADMREFAR2>
      <PRODOCDC2>
        <DocTypDC21>720</DocTypDC21>
        <DocRefDC23>EU_EXIT</DocRefDC23>
        <DocRefDCLNG>EN</DocRefDCLNG>
        <ComOfInfDC25>default</ComOfInfDC25>
        <ComOfInfDC25LNG>EN</ComOfInfDC25LNG>
      </PRODOCDC2>
      <PACGS2>
        <MarNumOfPacGS21>Bloomingales</MarNumOfPacGS21>
        <MarNumOfPacGS21LNG>EN</MarNumOfPacGS21LNG>
        <KinOfPacGS23>BX</KinOfPacGS23>
        <NumOfPacGS24>1</NumOfPacGS24>
      </PACGS2>
    </GOOITEGDS>
  </CC015B>

  lazy val CC015BRequiringDefaultGuarantee = <CC015B>
    <SynIdeMES1>UNOC</SynIdeMES1>
    <SynVerNumMES2>3</SynVerNumMES2>
    <MesRecMES6>NCTS</MesRecMES6>
    <DatOfPreMES9>20201217</DatOfPreMES9>
    <TimOfPreMES10>1340</TimOfPreMES10>
    <IntConRefMES11>17712576475433</IntConRefMES11>
    <AppRefMES14>NCTS</AppRefMES14>
    <MesIdeMES19>1</MesIdeMES19>
    <MesTypMES20>GB015B</MesTypMES20>
    <HEAHEA>
      <RefNumHEA4>GUATEST1201217134032</RefNumHEA4>
      <TypOfDecHEA24>T1</TypOfDecHEA24>
      <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>
      <AutLocOfGooCodHEA41>954131533-GB60DEP</AutLocOfGooCodHEA41>
      <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>
      <IdeOfMeaOfTraAtDHEA78>NC15 REG</IdeOfMeaOfTraAtDHEA78>
      <NatOfMeaOfTraAtDHEA80>GB</NatOfMeaOfTraAtDHEA80>
      <ConIndHEA96>0</ConIndHEA96>
      <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>
      <TotNumOfIteHEA305>1</TotNumOfIteHEA305>
      <TotNumOfPacHEA306>10</TotNumOfPacHEA306>
      <TotGroMasHEA307>1000</TotGroMasHEA307>
      <DecDatHEA383>20201217</DecDatHEA383>
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
      <ArrTimTRACUS085>202012191340</ArrTimTRACUS085>
    </CUSOFFTRARNS>
    <CUSOFFDESEST>
      <RefNumEST1>IT018100</RefNumEST1>
    </CUSOFFDESEST>
    <CONRESERS>
      <ConResCodERS16>A3</ConResCodERS16>
      <DatLimERS69>20201225</DatLimERS69>
    </CONRESERS>
    <SEAINFSLI>
      <SeaNumSLI2>1</SeaNumSLI2>
      <SEAIDSID>
        <SeaIdeSID1>NCTS001</SeaIdeSID1>
      </SEAIDSID>
    </SEAINFSLI>
    <GUAGUA>
      <GuaTypGUA1>0</GuaTypGUA1>
      <GUAREFREF>
        <GuaRefNumGRNREF1>20GB0000010000H72</GuaRefNumGRNREF1>
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
        <AddInfMT21>20GB0000010000H72</AddInfMT21>
        <AddInfCodMT23>CAL</AddInfCodMT23>
      </SPEMENMT2>
      <PACGS2>
        <MarNumOfPacGS21>AB234</MarNumOfPacGS21>
        <KinOfPacGS23>BX</KinOfPacGS23>
        <NumOfPacGS24>10</NumOfPacGS24>
      </PACGS2>
    </GOOITEGDS>
  </CC015B>

  lazy val CC015BWithMultipleGoodsItems = <CC015B>
    <SynIdeMES1>UNOC</SynIdeMES1>
    <SynVerNumMES2>3</SynVerNumMES2>
    <MesRecMES6>NCTS</MesRecMES6>
    <DatOfPreMES9>20201217</DatOfPreMES9>
    <TimOfPreMES10>1340</TimOfPreMES10>
    <IntConRefMES11>17712576475433</IntConRefMES11>
    <AppRefMES14>NCTS</AppRefMES14>
    <MesIdeMES19>1</MesIdeMES19>
    <MesTypMES20>GB015B</MesTypMES20>
    <HEAHEA>
      <RefNumHEA4>GUATEST1201217134032</RefNumHEA4>
      <TypOfDecHEA24>T1</TypOfDecHEA24>
      <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>
      <AutLocOfGooCodHEA41>954131533-GB60DEP</AutLocOfGooCodHEA41>
      <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>
      <IdeOfMeaOfTraAtDHEA78>NC15 REG</IdeOfMeaOfTraAtDHEA78>
      <NatOfMeaOfTraAtDHEA80>GB</NatOfMeaOfTraAtDHEA80>
      <ConIndHEA96>0</ConIndHEA96>
      <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>
      <TotNumOfIteHEA305>1</TotNumOfIteHEA305>
      <TotNumOfPacHEA306>10</TotNumOfPacHEA306>
      <TotGroMasHEA307>1000</TotGroMasHEA307>
      <DecDatHEA383>20201217</DecDatHEA383>
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
      <ArrTimTRACUS085>202012191340</ArrTimTRACUS085>
    </CUSOFFTRARNS>
    <CUSOFFDESEST>
      <RefNumEST1>IT018100</RefNumEST1>
    </CUSOFFDESEST>
    <CONRESERS>
      <ConResCodERS16>A3</ConResCodERS16>
      <DatLimERS69>20201225</DatLimERS69>
    </CONRESERS>
    <SEAINFSLI>
      <SeaNumSLI2>1</SeaNumSLI2>
      <SEAIDSID>
        <SeaIdeSID1>NCTS001</SeaIdeSID1>
      </SEAIDSID>
    </SEAINFSLI>
    <GUAGUA>
      <GuaTypGUA1>0</GuaTypGUA1>
      <GUAREFREF>
        <GuaRefNumGRNREF1>20GB0000010000H72</GuaRefNumGRNREF1>
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
        <AddInfMT21>20GB0000010000H72</AddInfMT21>
        <AddInfCodMT23>CAL</AddInfCodMT23>
      </SPEMENMT2>
      <PACGS2>
        <MarNumOfPacGS21>AB234</MarNumOfPacGS21>
        <KinOfPacGS23>BX</KinOfPacGS23>
        <NumOfPacGS24>10</NumOfPacGS24>
      </PACGS2>
    </GOOITEGDS>
    <GOOITEGDS>
      <IteNumGDS7>2</IteNumGDS7>
      <ComCodTarCodGDS10>default</ComCodTarCodGDS10>
      <DecTypGDS15>default</DecTypGDS15>
      <GooDesGDS23>Flowers</GooDesGDS23>
      <GooDesGDS23LNG>EN</GooDesGDS23LNG>
      <GroMasGDS46>1000</GroMasGDS46>
      <NetMasGDS48>999</NetMasGDS48>
      <CouOfDesGDS59>ex</CouOfDesGDS59>
      <PREADMREFAR2>
        <PreDocTypAR21>T2</PreDocTypAR21>
        <PreDocRefAR26>EU_EXIT-T2</PreDocRefAR26>
        <PreDocRefLNG>EN</PreDocRefLNG>
        <ComOfInfAR29>default</ComOfInfAR29>
        <ComOfInfAR29LNG>EN</ComOfInfAR29LNG>
      </PREADMREFAR2>
      <PRODOCDC2>
        <DocTypDC21>720</DocTypDC21>
        <DocRefDC23>EU_EXIT</DocRefDC23>
        <DocRefDCLNG>EN</DocRefDCLNG>
        <ComOfInfDC25>default</ComOfInfDC25>
        <ComOfInfDC25LNG>EN</ComOfInfDC25LNG>
      </PRODOCDC2>
      <PACGS2>
        <MarNumOfPacGS21>Bloomingales</MarNumOfPacGS21>
        <MarNumOfPacGS21LNG>EN</MarNumOfPacGS21LNG>
        <KinOfPacGS23>BX</KinOfPacGS23>
        <NumOfPacGS24>1</NumOfPacGS24>
      </PACGS2>
    </GOOITEGDS>
  </CC015B>

  lazy val CC015BwithMesSenMES3 = <CC015B>
    <SynIdeMES1>UNOC</SynIdeMES1>
    <SynVerNumMES2>3</SynVerNumMES2>
    <MesSenMES3>nope</MesSenMES3>
    <MesRecMES6>NCTS</MesRecMES6>
    <DatOfPreMES9>20190912</DatOfPreMES9>
    <TimOfPreMES10>1222</TimOfPreMES10>
    <IntConRefMES11>WE190912102530</IntConRefMES11>
    <AppRefMES14>NCTS</AppRefMES14>
    <TesIndMES18>0</TesIndMES18>
    <MesIdeMES19>1</MesIdeMES19>
    <MesTypMES20>GB015B</MesTypMES20>
    <HEAHEA>
      <RefNumHEA4>01CTC201909121215</RefNumHEA4>
      <TypOfDecHEA24>T2</TypOfDecHEA24>
      <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>
      <AgrLocOfGooCodHEA38>default</AgrLocOfGooCodHEA38>
      <AgrLocOfGooHEA39>default</AgrLocOfGooHEA39>
      <AgrLocOfGooHEA39LNG>EN</AgrLocOfGooHEA39LNG>
      <AutLocOfGooCodHEA41>default</AutLocOfGooCodHEA41>
      <PlaOfLoaCodHEA46>DOVER</PlaOfLoaCodHEA46>
      <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>
      <CusSubPlaHEA66>default</CusSubPlaHEA66>
      <InlTraModHEA75>20</InlTraModHEA75>
      <IdeOfMeaOfTraAtDHEA78>EU_EXIT</IdeOfMeaOfTraAtDHEA78>
      <IdeOfMeaOfTraAtDHEA78LNG>EN</IdeOfMeaOfTraAtDHEA78LNG>
      <IdeOfMeaOfTraCroHEA85>EU_EXIT</IdeOfMeaOfTraCroHEA85>
      <IdeOfMeaOfTraCroHEA85LNG>EN</IdeOfMeaOfTraCroHEA85LNG>
      <ConIndHEA96>0</ConIndHEA96>
      <DiaLanIndAtDepHEA254>EN</DiaLanIndAtDepHEA254>
      <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>
      <TotNumOfIteHEA305>1</TotNumOfIteHEA305>
      <TotNumOfPacHEA306>1</TotNumOfPacHEA306>
      <TotGroMasHEA307>1000</TotGroMasHEA307>
      <DecDatHEA383>20190912</DecDatHEA383>
      <DecPlaHEA394>DOVER</DecPlaHEA394>
      <DecPlaHEA394LNG>EN</DecPlaHEA394LNG>
    </HEAHEA>
    <TRAPRIPC1>
      <NamPC17>CITY WATCH SHIPPING</NamPC17>
      <StrAndNumPC122>125 Psuedopolis Yard</StrAndNumPC122>
      <PosCodPC123>SS99 1AA</PosCodPC123>
      <CitPC124>Ank-Morpork</CitPC124>
      <CouPC125>GB</CouPC125>
      <NADLNGPC>EN</NADLNGPC>
      <TINPC159>GB652420267000</TINPC159>
    </TRAPRIPC1>
    <TRACONCO1>
      <NamCO17>QUIRM ENGINEERING</NamCO17>
      <StrAndNumCO122>125 Psuedopolis Yard</StrAndNumCO122>
      <PosCodCO123>SS99 1AA</PosCodCO123>
      <CitCO124>Ank-Morpork</CitCO124>
      <CouCO125>GB</CouCO125>
      <TINCO159>GB602070107000</TINCO159>
    </TRACONCO1>
    <TRACONCE1>
      <NamCE17>DROFL POTTERY</NamCE17>
      <StrAndNumCE122>125 Psuedopolis Yard</StrAndNumCE122>
      <PosCodCE123>SS99 1AA</PosCodCE123>
      <CitCE124>Ank-Morpork</CitCE124>
      <CouCE125>GB</CouCE125>
      <NADLNGCE>EN</NADLNGCE>
      <TINCE159>GB658120050000</TINCE159>
    </TRACONCE1>
    <CUSOFFDEPEPT>
      <RefNumEPT1>GB000060</RefNumEPT1>
    </CUSOFFDEPEPT>
    <CUSOFFTRARNS>
      <RefNumRNS1>FR001260</RefNumRNS1>
      <ArrTimTRACUS085>201909160100</ArrTimTRACUS085>
    </CUSOFFTRARNS>
    <CUSOFFDESEST>
      <RefNumEST1>IT021100</RefNumEST1>
    </CUSOFFDESEST>
    <SEAINFSLI>
      <SeaNumSLI2>1</SeaNumSLI2>
      <SEAIDSID>
        <SeaIdeSID1>Seal001</SeaIdeSID1>
        <SeaIdeSID1LNG>EN</SeaIdeSID1LNG>
      </SEAIDSID>
    </SEAINFSLI>
    <GUAGUA>
      <GuaTypGUA1>3</GuaTypGUA1>
      <GUAREFREF>
        <GuaRefNumGRNREF1>default</GuaRefNumGRNREF1>
        <OthGuaRefREF4>EU_EXIT</OthGuaRefREF4>
        <AccCodREF6>test</AccCodREF6>
      </GUAREFREF>
    </GUAGUA>
    <GOOITEGDS>
      <IteNumGDS7>1</IteNumGDS7>
      <ComCodTarCodGDS10>default</ComCodTarCodGDS10>
      <DecTypGDS15>default</DecTypGDS15>
      <GooDesGDS23>Flowers</GooDesGDS23>
      <GooDesGDS23LNG>EN</GooDesGDS23LNG>
      <GroMasGDS46>1000</GroMasGDS46>
      <NetMasGDS48>999</NetMasGDS48>
      <CouOfDesGDS59>ex</CouOfDesGDS59>
      <PREADMREFAR2>
        <PreDocTypAR21>T2</PreDocTypAR21>
        <PreDocRefAR26>EU_EXIT-T2</PreDocRefAR26>
        <PreDocRefLNG>EN</PreDocRefLNG>
        <ComOfInfAR29>default</ComOfInfAR29>
        <ComOfInfAR29LNG>EN</ComOfInfAR29LNG>
      </PREADMREFAR2>
      <PRODOCDC2>
        <DocTypDC21>720</DocTypDC21>
        <DocRefDC23>EU_EXIT</DocRefDC23>
        <DocRefDCLNG>EN</DocRefDCLNG>
        <ComOfInfDC25>default</ComOfInfDC25>
        <ComOfInfDC25LNG>EN</ComOfInfDC25LNG>
      </PRODOCDC2>
      <PACGS2>
        <MarNumOfPacGS21>Bloomingales</MarNumOfPacGS21>
        <MarNumOfPacGS21LNG>EN</MarNumOfPacGS21LNG>
        <KinOfPacGS23>BX</KinOfPacGS23>
        <NumOfPacGS24>1</NumOfPacGS24>
      </PACGS2>
    </GOOITEGDS>
  </CC015B>

  lazy val InvalidCC015B = <CC015B>
    <SynIdeMES1>UNOC</SynIdeMES1>
    <SynVerNumMES2>3</SynVerNumMES2>
    <MesRecMES6>NCTS</MesRecMES6>
    <DatOfPreMES9>20190912</DatOfPreMES9>
    <TimOfPreMES10>1222</TimOfPreMES10>
    <IntConRefMES11>WE190912102530</IntConRefMES11>
    <AppRefMES14>NCTS</AppRefMES14>
    <TesIndMES18>0</TesIndMES18>
    <MesIdeMES19>1</MesIdeMES19>
    <MesTypMES20>GB015B</MesTypMES20>
    <HEAHEA>
      <RefNumHEA4>01CTC201909121215</RefNumHEA4>
      <TypOfDecHEA24>T2</TypOfDecHEA24>
      <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>
      <AgrLocOfGooCodHEA38>default</AgrLocOfGooCodHEA38>
      <AgrLocOfGooHEA39>default</AgrLocOfGooHEA39>
      <AgrLocOfGooHEA39LNG>EN</AgrLocOfGooHEA39LNG>
      <AutLocOfGooCodHEA41>default</AutLocOfGooCodHEA41>
      <PlaOfLoaCodHEA46>DOVER</PlaOfLoaCodHEA46>
      <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>
      <CusSubPlaHEA66>default</CusSubPlaHEA66>
      <InlTraModHEA75>20</InlTraModHEA75>
      <IdeOfMeaOfTraAtDHEA78>EU_EXIT</IdeOfMeaOfTraAtDHEA78>
      <IdeOfMeaOfTraAtDHEA78LNG>EN</IdeOfMeaOfTraAtDHEA78LNG>
      <IdeOfMeaOfTraCroHEA85>EU_EXIT</IdeOfMeaOfTraCroHEA85>
      <IdeOfMeaOfTraCroHEA85LNG>EN</IdeOfMeaOfTraCroHEA85LNG>
      <ConIndHEA96>0</ConIndHEA96>
      <DiaLanIndAtDepHEA254>EN</DiaLanIndAtDepHEA254>
      <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>
      <TotNumOfIteHEA305>1</TotNumOfIteHEA305>
      <TotNumOfPacHEA306>1</TotNumOfPacHEA306>
      <TotGroMasHEA307>1000</TotGroMasHEA307>
      <DecDatHEA383>20190912</DecDatHEA383>
      <DecPlaHEA394>DOVER</DecPlaHEA394>
      <DecPlaHEA394LNG>EN</DecPlaHEA394LNG>
    </HEAHEA>
    <TRAPRIPC1>
      <NamPC17>CITY WATCH SHIPPING</NamPC17>
      <StrAndNumPC122>125 Psuedopolis Yard</StrAndNumPC122>
      <PosCodPC123>SS99 1AA</PosCodPC123>
      <CitPC124>Ank-Morpork</CitPC124>
      <CouPC125>GB</CouPC125>
      <NADLNGPC>EN</NADLNGPC>
      <TINPC159>GB652420267000</TINPC159>
    </TRAPRIPC1>
    <TRACONCO1>
      <NamCO17>QUIRM ENGINEERING</NamCO17>
      <StrAndNumCO122>125 Psuedopolis Yard</StrAndNumCO122>
      <PosCodCO123>SS99 1AA</PosCodCO123>
      <CitCO124>Ank-Morpork</CitCO124>
      <CouCO125>GB</CouCO125>
      <TINCO159>GB602070107000</TINCO159>
    </TRACONCO1>
    <TRACONCE1>
      <NamCE17>DROFL POTTERY</NamCE17>
      <StrAndNumCE122>125 Psuedopolis Yard</StrAndNumCE122>
      <PosCodCE123>SS99 1AA</PosCodCE123>
      <CitCE124>Ank-Morpork</CitCE124>
      <CouCE125>GB</CouCE125>
      <NADLNGCE>EN</NADLNGCE>
      <TINCE159>GB658120050000</TINCE159>
    </TRACONCE1>
    <CUSOFFDEPEPT>
      <RefNumEPT1>GB000060</RefNumEPT1>
    </CUSOFFDEPEPT>
    <CUSOFFTRARNS>
      <RefNumRNS1>FR001260</RefNumRNS1>
      <ArrTimTRACUS085>201909160100</ArrTimTRACUS085>
    </CUSOFFTRARNS>
    <CUSOFFDESEST>
      <RefNumEST1>IT021100</RefNumEST1>
    </CUSOFFDESEST>
    <SEAINFSLI>
      <SeaNumSLI2>1</SeaNumSLI2>
      <SEAIDSID>
        <SeaIdeSID1>Seal001</SeaIdeSID1>
        <SeaIdeSID1LNG>EN</SeaIdeSID1LNG>
      </SEAIDSID>
    </SEAINFSLI>
    <GUAGUA>
      <GuaTypGUA1>3</GuaTypGUA1>
      <GUAREFREF>
        <GuaRefNumGRNREF1>default</GuaRefNumGRNREF1>
        <OthGuaRefREF4>EU_EXIT</OthGuaRefREF4>
        <AccCodREF6>thisIsTooLong</AccCodREF6>
      </GUAREFREF>
    </GUAGUA>
    <GOOITEGDS>
      <IteNumGDS7>1</IteNumGDS7>
      <ComCodTarCodGDS10>default</ComCodTarCodGDS10>
      <DecTypGDS15>default</DecTypGDS15>
      <GooDesGDS23>Flowers</GooDesGDS23>
      <GooDesGDS23LNG>EN</GooDesGDS23LNG>
      <GroMasGDS46>1000</GroMasGDS46>
      <NetMasGDS48>999</NetMasGDS48>
      <CouOfDesGDS59>ex</CouOfDesGDS59>
      <PREADMREFAR2>
        <PreDocTypAR21>T2</PreDocTypAR21>
        <PreDocRefAR26>EU_EXIT-T2</PreDocRefAR26>
        <PreDocRefLNG>EN</PreDocRefLNG>
        <ComOfInfAR29>default</ComOfInfAR29>
        <ComOfInfAR29LNG>EN</ComOfInfAR29LNG>
      </PREADMREFAR2>
      <PRODOCDC2>
        <DocTypDC21>720</DocTypDC21>
        <DocRefDC23>EU_EXIT</DocRefDC23>
        <DocRefDCLNG>EN</DocRefDCLNG>
        <ComOfInfDC25>default</ComOfInfDC25>
        <ComOfInfDC25LNG>EN</ComOfInfDC25LNG>
      </PRODOCDC2>
      <PACGS2>
        <MarNumOfPacGS21>Bloomingales</MarNumOfPacGS21>
        <MarNumOfPacGS21LNG>EN</MarNumOfPacGS21LNG>
        <KinOfPacGS23>BX</KinOfPacGS23>
        <NumOfPacGS24>1</NumOfPacGS24>
      </PACGS2>
    </GOOITEGDS>
  </CC015B>

  lazy val exampleGOOITEGDSSequence =
    <example>
      <GOOITEGDS>
        <IteNumGDS7>1</IteNumGDS7>
        <SPEMENMT2>
          <AddInfMT21>7000.0EUR07IT00000100000Z1</AddInfMT21>
          <AddInfCodMT23>CAL</AddInfCodMT23>
        </SPEMENMT2>
        <SPEMENMT2>
          <AddInfMT21>7000.0EUR07IT00000100000Z3</AddInfMT21>
          <AddInfCodMT23>CAL</AddInfCodMT23>
        </SPEMENMT2>
        <SPEMENMT2>
          <AddInfMT21>7000.0EUR07IT00000100000Z9</AddInfMT21>
          <AddInfCodMT23>CAL</AddInfCodMT23>
        </SPEMENMT2>
        <SPEMENMT2>
          <AddInfMT21>EU_EXIT</AddInfMT21>
          <AddInfMT21LNG>EN</AddInfMT21LNG>
          <AddInfCodMT23>DG1</AddInfCodMT23>
          <ExpFroCouMT25>AD</ExpFroCouMT25>
        </SPEMENMT2>
      </GOOITEGDS>
    </example>

  lazy val exampleGOOITEGDS = <GOOITEGDS>
    <IteNumGDS7>1</IteNumGDS7>
    <SPEMENMT2>
      <AddInfMT21>7000.0EUR07IT00000100000Z1</AddInfMT21>
      <AddInfCodMT23>CAL</AddInfCodMT23>
    </SPEMENMT2>
    <SPEMENMT2>
      <AddInfMT21>7000.0EUR07IT00000100000Z3</AddInfMT21>
      <AddInfCodMT23>CAL</AddInfCodMT23>
    </SPEMENMT2>
    <SPEMENMT2>
      <AddInfMT21>7000.0EUR07IT00000100000Z9</AddInfMT21>
      <AddInfCodMT23>CAL</AddInfCodMT23>
    </SPEMENMT2>
    <SPEMENMT2>
      <AddInfMT21>EU_EXIT</AddInfMT21>
      <AddInfMT21LNG>EN</AddInfMT21LNG>
      <AddInfCodMT23>DG1</AddInfCodMT23>
      <ExpFroCouMT25>AD</ExpFroCouMT25>
    </SPEMENMT2>
  </GOOITEGDS>

  def exampleGuaranteeGuaTypGUA1(gType: Char): Elem =
    <GUAGUA>
      <GuaTypGUA1>{gType}</GuaTypGUA1>
      <GUAREFREF>
        <GuaRefNumGRNREF1>07IT00000100000Z3</GuaRefNumGRNREF1>
      </GUAREFREF>
    </GUAGUA>

  def exampleOtherGuaranteeGuaTypGUA1(gType: Char): Elem =
    <GUAGUA>
      <GuaTypGUA1>{gType}</GuaTypGUA1>
      <GUAREFREF>
        <OthGuaRefREF4>SomeValue</OthGuaRefREF4>
      </GUAREFREF>
    </GUAGUA>

  def exampleMultiGuaranteeGuaTypGUA1(gType: Char, count: Int): Elem =
    <GUAGUA>
      <GuaTypGUA1>{gType}</GuaTypGUA1>
      {generateGuaranteeReference(gType, count)}
    </GUAGUA>

  def generateGuaranteeReference(gType: Char, count: Int): NodeSeq = {
    @tailrec
    def gen_internal(c: Int, accumulator: NodeSeq): NodeSeq =
      c match {
        case 0 => accumulator
        case _ =>
          gen_internal(
            c - 1,
            accumulator ++ {
              if (!Guarantee.isOther(gType)) {
                <GUAREFREF>
                  <GuaRefNumGRNREF1>07IT00000100000Z3</GuaRefNumGRNREF1>
                </GUAREFREF>
              } else {
                <GUAREFREF>
                  <OthGuaRefREF4>SomeValue</OthGuaRefREF4>
                </GUAREFREF>
              }
            }
          )
      }

    gen_internal(count, NodeSeq.Empty)
  }

  lazy val exampleGuaranteeGuaTypGUA1BadGuaType =
    <GUAGUA>
      <GuaTypGUA1>A</GuaTypGUA1>
      <GUAREFREF>
        <GuaRefNumGRNREF1>07IT00000100000Z3</GuaRefNumGRNREF1>
      </GUAREFREF>
    </GUAGUA>

  lazy val exampleGuaranteeGuaTypGUA1MissingGuaType =
    <GUAGUA>
      <GuaTypGUA1></GuaTypGUA1>
      <GUAREFREF>
        <GuaRefNumGRNREF1>07IT00000100000Z3</GuaRefNumGRNREF1>
      </GUAREFREF>
    </GUAGUA>

  lazy val exampleGuaranteeGuaTypGUA1EmptyReference =
    <GUAGUA>
      <GuaTypGUA1></GuaTypGUA1>
      <GUAREFREF>
        <GuaRefNumGRNREF1>07IT00000100000Z3</GuaRefNumGRNREF1>
      </GUAREFREF>
    </GUAGUA>

  lazy val exampleGuaranteeSPEMENMT2 =
    <SPEMENMT2>
    <AddInfMT21>7000.0EUR07IT00000100000Z3</AddInfMT21>
    <AddInfCodMT23>CAL</AddInfCodMT23>
  </SPEMENMT2>

  lazy val exampleOtherSPEMENMT2 =
    <SPEMENMT2>
      <AddInfMT21>7000.0EUR07IT00000100000Z3</AddInfMT21>
      <AddInfCodMT23>Bleep</AddInfCodMT23>
    </SPEMENMT2>

  lazy val exampleAdditionalInfoMissing =
    <SPEMENMT2>
      <AddInfMT21></AddInfMT21>
      <AddInfCodMT23>Bleep</AddInfCodMT23>
    </SPEMENMT2>

  lazy val exampleCodeMissing =
    <SPEMENMT2>
      <AddInfMT21>7000.0EUR07IT00000100000Z3</AddInfMT21>
      <AddInfCodMT23></AddInfCodMT23>
    </SPEMENMT2>

}
