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

package controllers.actions

import config.Constants
import data.TestXml
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import utils.Utils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ValidateAcceptJsonHeaderActionSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach with TestXml {
  override lazy val app = GuiceApplicationBuilder()
    .overrides(bind[AuthAction].to[FakeAuthAction])
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  class Harness(validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction, cc: ControllerComponents) extends BackendController(cc) {
    def post: Action[AnyContent] = (DefaultActionBuilder.apply(cc.parsers.anyContent) andThen validateAcceptJsonHeaderAction).async(cc.parsers.anyContent) {
      _ =>
        Future.successful(Ok)
    }
  }

  "ValidateAcceptJsonHeaderAction" - {
    "must execute the block when correct Accept header is passed in" in {
      val validateAcceptJsonHeaderAction = app.injector.instanceOf[ValidateAcceptJsonHeaderAction]
      val cc = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateAcceptJsonHeaderAction, cc)

      val req: FakeRequest[AnyContent] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")), AnyContentAsEmpty)

      val result = controller.post()(req)

      status(result) mustEqual OK
    }

    "must return NotAcceptable if Accept header with invalid version is passed in" in {
      val validateAcceptJsonHeaderAction = app.injector.instanceOf[ValidateAcceptJsonHeaderAction]
      val cc = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateAcceptJsonHeaderAction, cc)

      val req: FakeRequest[AnyContent] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json")), AnyContentAsEmpty)

      val result = controller.post()(req)

      status(result) mustEqual NOT_ACCEPTABLE
      contentAsString(result) mustEqual Json.toJson(ErrorResponse(NOT_ACCEPTABLE, Constants.AcceptHeaderMissing)).toString()
    }

    "must return NotAcceptable if Accept header is missing" in {
      val validateAcceptJsonHeaderAction = app.injector.instanceOf[ValidateAcceptJsonHeaderAction]
      val cc = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateAcceptJsonHeaderAction, cc)

      val req: FakeRequest[AnyContent] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq()), AnyContentAsEmpty)

      val result = controller.post()(req)

      status(result) mustEqual NOT_ACCEPTABLE
      contentAsString(result) mustEqual Json.toJson(ErrorResponse(NOT_ACCEPTABLE, Constants.AcceptHeaderMissing)).toString()
    }

    "must return NotAcceptable if Accept header is empty" in {
      val validateAcceptJsonHeaderAction = app.injector.instanceOf[ValidateAcceptJsonHeaderAction]
      val cc = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateAcceptJsonHeaderAction, cc)

      val req: FakeRequest[AnyContent] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "")), AnyContentAsEmpty)

      val result = controller.post()(req)

      status(result) mustEqual NOT_ACCEPTABLE
      contentAsString(result) mustEqual Json.toJson(ErrorResponse(NOT_ACCEPTABLE, Constants.AcceptHeaderMissing)).toString()
    }

    "must return NotAcceptable if Accept header is */*" in {
      val validateAcceptJsonHeaderAction = app.injector.instanceOf[ValidateAcceptJsonHeaderAction]
      val cc = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateAcceptJsonHeaderAction, cc)

      val req: FakeRequest[AnyContent] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "*/*")), AnyContentAsEmpty)

      val result = controller.post()(req)

      status(result) mustEqual NOT_ACCEPTABLE
      contentAsString(result) mustEqual Json.toJson(ErrorResponse(NOT_ACCEPTABLE, Constants.AcceptHeaderMissing)).toString()
    }

    "must return NotAcceptable if Accept header is application/xml" in {
      val validateAcceptJsonHeaderAction = app.injector.instanceOf[ValidateAcceptJsonHeaderAction]
      val cc = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateAcceptJsonHeaderAction, cc)

      val req: FakeRequest[AnyContent] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/json")), AnyContentAsEmpty)

      val result = controller.post()(req)

      status(result) mustEqual NOT_ACCEPTABLE
      contentAsString(result) mustEqual Json.toJson(ErrorResponse(NOT_ACCEPTABLE, Constants.AcceptHeaderMissing)).toString()
    }

    "must return NotAcceptable if Accept header is text/html" in {
      val validateAcceptJsonHeaderAction = app.injector.instanceOf[ValidateAcceptJsonHeaderAction]
      val cc = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateAcceptJsonHeaderAction, cc)

      val req: FakeRequest[AnyContent] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "text/html")), AnyContentAsEmpty)

      val result = controller.post()(req)

      status(result) mustEqual NOT_ACCEPTABLE
      contentAsString(result) mustEqual Json.toJson(ErrorResponse(NOT_ACCEPTABLE, Constants.AcceptHeaderMissing)).toString()
    }
  }
}
