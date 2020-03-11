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

import controllers.actions.{AuthAction, FakeAuthAction}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import config.AppConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.mvc.{AnyContentAsEmpty, BodyParsers}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.test.Helpers._

import scala.xml.NodeSeq
import services.XmlValidationService
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.ExecutionContext.Implicits.global

class ArrivalsControllerSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit def appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  val xmlValidation = new XmlValidationService

  val cc = stubControllerComponents()

  val config: AppConfig = app.injector.instanceOf[AppConfig]

  val parser = app.injector.instanceOf[BodyParsers.Default]

  val authConnector = mock[AuthConnector]
  val fakeAuthAction = FakeAuthAction(authConnector, config, parser)

  object TestArrivalsController extends ArrivalsController(cc, fakeAuthAction, xmlValidation)

  def ctcFakeRequest() = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(), body = AnyContentAsEmpty)

  def ctcFakeRequestXML() =
    FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), body = AnyContentAsEmpty)

  def ctcFakeRequestXML(body: NodeSeq) =
    FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), body)

 "POST /movements/arrivals" - {
   "must return Accepted" in {
     val request = ctcFakeRequestXML(
       <CC007A>
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
     )
     val result = TestArrivalsController.createArrivalNotification().apply(request)

     status(result) mustBe ACCEPTED
   }

   "must return UnsupportedMediaType when Content-Type is JSON" in {
     val request = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json")), body = AnyContentAsEmpty)

     val result = TestArrivalsController.createArrivalNotification().apply(request)

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return UnsupportedMediaType when no Content-Type specified" in {
     val request = ctcFakeRequest().withRawBody(ByteString("body"))

     val result = TestArrivalsController.createArrivalNotification().apply(request)

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return UnsupportedMediaType when empty XML payload is sent" in {
     val request = ctcFakeRequest()

     val result = TestArrivalsController.createArrivalNotification().apply(request)

     status(result) mustBe UNSUPPORTED_MEDIA_TYPE
   }

   "must return BadRequest when invalid XML payload is sent" in {
     val request = ctcFakeRequestXML(<message>007</message>)

     val result = TestArrivalsController.createArrivalNotification().apply(request)

     status(result) mustBe BAD_REQUEST
   }

   "must return BadRequest when XML payload is missing elements" in {
     val request = ctcFakeRequestXML(
       <CC007A>
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
       </CC007A>
     )

     val result = TestArrivalsController.createArrivalNotification().apply(request)

     status(result) mustBe BAD_REQUEST
   }
 }
}
