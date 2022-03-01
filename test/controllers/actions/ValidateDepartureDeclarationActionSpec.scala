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

package controllers.actions

import data.TestXml
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.DefaultActionBuilder
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.test.Helpers.status
import services.XmlError
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.NodeSeq

class ValidateDepartureDeclarationActionSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterEach
    with TestXml {

  override lazy val app = GuiceApplicationBuilder()
    .overrides(bind[AuthAction].to[FakeAuthAction])
    .build()

  override def beforeEach(): Unit =
    super.beforeEach()

  class Harness(validateAction: ValidateDepartureDeclarationAction, cc: ControllerComponents) extends BackendController(cc) {

    def post: Action[NodeSeq] = (DefaultActionBuilder.apply(cc.parsers.anyContent) andThen validateAction).async(cc.parsers.xml) {
      _ =>
        Future.successful(Ok)
    }

    def postContent: Action[AnyContent] =
      (DefaultActionBuilder.apply(cc.parsers.anyContent) andThen validateAction).async {
        _ =>
          Future.successful(Ok)
      }
  }

  "ValidateDepartureDeclarationAction" - {
    "must execute the block when passed in a valid IE015B xml request" in {
      val validateMessage = app.injector.instanceOf[ValidateDepartureDeclarationAction]
      val cc              = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), CC015B)

      val result = controller.post()(req)

      status(result) mustEqual OK
    }

    "must return BadRequest when passed in an invalid IE015B xml request " in {
      val validateMessage = app.injector.instanceOf[ValidateDepartureDeclarationAction]
      val cc              = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] =
        FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), InvalidCC015B)

      val result = controller.post()(req)

      val expectedMessage = "[{\"lineNumber\":86,\"columnNumber\":47,\"message\":\"cvc-pattern-valid: Value 'thisIsTooLong' is not facet-valid with respect to pattern '.{4}' for type 'Alphanumeric_4_-25'.\"},{\"lineNumber\":86,\"columnNumber\":47,\"message\":\"cvc-type.3.1.3: The value 'thisIsTooLong' of element 'AccCodREF6' is not valid.\"}]"

      status(result) mustEqual BAD_REQUEST
      contentAsString(result) mustEqual expectedMessage
    }

    "must return BadRequest when passed in an empty request" in {
      val validateMessage = app.injector.instanceOf[ValidateDepartureDeclarationAction]
      val cc              = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] =
        FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), NodeSeq.Empty)

      val result = controller.post()(req)

      status(result) mustEqual BAD_REQUEST
      contentAsString(result) mustEqual XmlError.RequestBodyEmptyMessage
    }

    "must return BadRequest when passed in incorrect request body" in {
      val validateMessage = app.injector.instanceOf[ValidateDepartureDeclarationAction]
      val cc              = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val exampleRequest: JsValue = Json.parse(
        """{
          |     "data": {
          |         "field": "value"
          |     }
          | }""".stripMargin
      )

      val req = FakeRequest(method = "", path = "").withHeaders(FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/json"))).withJsonBody(exampleRequest)

      val result = controller.postContent()(req)

      status(result) mustEqual BAD_REQUEST
      contentAsString(result) mustEqual XmlError.RequestBodyInvalidTypeMessage
    }

    "must return BadRequest when passed in an incorrect XML request" in {
      val validateMessage = app.injector.instanceOf[ValidateDepartureDeclarationAction]
      val cc              = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] =
        FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), <CC008A></CC008A>)

      val result = controller.post()(req)

      status(result) mustEqual BAD_REQUEST
    }
  }
}
