///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package controllers.actions
//TODO: Create the equivalent test for new validated action
//import base.TestActorSystem
//import config.AppConfig
//import controllers.actions.providers.AcceptHeaderActionProviderImpl
//import data.TestXml
//import models.*
//import org.mockito.Mockito.when
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.freespec.AnyFreeSpec
//import org.scalatest.matchers.must.Matchers
//import org.scalatestplus.mockito.MockitoSugar
//import play.api.http.Status.NOT_ACCEPTABLE
//import play.api.http.Status.OK
//import play.api.http.HeaderNames
//import play.api.http.HttpVerbs
//import play.api.mvc.*
//import play.api.test.Helpers.defaultAwaitTimeout
//import play.api.test.Helpers.status
//import play.api.test.FakeHeaders
//import play.api.test.FakeRequest
//import play.api.test.Helpers
//import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
//
//import scala.concurrent.ExecutionContextExecutor
//import scala.concurrent.Future
//
//class AcceptHeaderActionSpec extends AnyFreeSpec with Matchers with ScalaFutures with MockitoSugar with TestXml with TestActorSystem {
//
//  implicit val ec: ExecutionContextExecutor = materializer.executionContext
//
//  class Harness(acceptHeaderAction: AcceptHeaderAction[Request], cc: ControllerComponents) extends BackendController(cc) {
//
//    def post: Action[AnyContent] = (DefaultActionBuilder.apply(cc.parsers.anyContent) andThen acceptHeaderAction).async {
//      _ =>
//        Future.successful(Ok)
//    }
//  }
//
//  val appConfig: AppConfig = mock[AppConfig]
//  when(appConfig.smallMessageSizeLimit).thenReturn(500000)
//
//  val acceptHeaderAction       = new AcceptHeaderActionProviderImpl()
//  val cc: ControllerComponents = Helpers.stubControllerComponents()
//
//  "AcceptHeaderAction " - {
//
//    lazy val acceptedHeaders = Seq(
//      VersionedJsonHeader(Version2_1),
//      VersionedJsonPlusXmlHeader(Version2_1),
//      VersionedXmlHeader(Version2_1)
//    )
//
//    for (acceptHeader <- acceptedHeaders)
//      s"must allow accept header if it is present in the list passed to the action ($acceptHeader)" in {
//        val controller = new Harness(acceptHeaderAction(acceptedHeaders), cc)
//        val req: FakeRequest[AnyContent] = FakeRequest(
//          method = HttpVerbs.GET,
//          uri = "",
//          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeader.value)),
//          AnyContent.apply()
//        )
//        val result = controller.post()(req)
//
//        status(result) mustEqual OK
//      }
//
//    lazy val mixedCaseAcceptHeaders = Seq(
//      "application/vnd.hmrc.2.1+jSon",
//      "application/vnd.hmrc.2.1+jSOn+xml",
//      "application/vnd.hmrc.2.1+Xml"
//    )
//
//    for (acceptHeader <- mixedCaseAcceptHeaders)
//      s"must allow valid case-insensitive headers if present in the list passed to the action ($acceptHeader)" in {
//        val controller = new Harness(acceptHeaderAction(acceptedHeaders), cc)
//        val req: FakeRequest[AnyContent] = FakeRequest(
//          method = HttpVerbs.GET,
//          uri = "",
//          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeader)),
//          AnyContent.apply()
//        )
//        val result = controller.post()(req)
//
//        status(result) mustEqual OK
//      }
//
//    "must not allow an accept headers that is not present in the list passed to the action" in {
//      val controller = new Harness(acceptHeaderAction(acceptedHeaders), cc)
//      val req: FakeRequest[AnyContent] = FakeRequest(
//        method = HttpVerbs.GET,
//        uri = "",
//        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedJsonHyphenXmlHeader(Version2_1).value)),
//        AnyContent.apply()
//      )
//      val result = controller.post()(req)
//
//      status(result) mustEqual NOT_ACCEPTABLE
//
//    }
//
//  }
//}
