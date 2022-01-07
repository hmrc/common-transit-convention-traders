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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
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
import play.api.test.Helpers._
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import services.XmlError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.NodeSeq
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

class ValidateArrivalNotificationActionSpec
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

  class Harness(validateArrivalNotificationAction: ValidateArrivalNotificationAction, cc: ControllerComponents) extends BackendController(cc) {

    def post: Action[NodeSeq] = (DefaultActionBuilder.apply(cc.parsers.anyContent) andThen validateArrivalNotificationAction).async(cc.parsers.xml) {
      _ =>
        Future.successful(Ok)
    }

    def postContent: Action[AnyContent] =
      (DefaultActionBuilder.apply(cc.parsers.anyContent) andThen validateArrivalNotificationAction).async {
        _ =>
          Future.successful(Ok)
      }
  }

  "ValidateArrivalNotificationAction" - {
    "must execute the block when passed in a valid IE007 xml request" in {
      val validateMessage = app.injector.instanceOf[ValidateArrivalNotificationAction]
      val cc              = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), CC007A)

      val result = controller.post()(req)

      status(result) mustEqual OK
    }

    "must return BadRequest when passed in an invalid IE007 xml request " in {
      val validateMessage = app.injector.instanceOf[ValidateArrivalNotificationAction]
      val cc              = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] =
        FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), InvalidCC007A)

      val result = controller.post()(req)

      val expectedMessage =
        "The request has failed schema validation. Please review the required message structure as specified by the XSD file 'cc007a.xsd'. Detailed error below:\ncvc-complex-type.2.4.a: Invalid content was found starting with element 'CUSOFFPREOFFRES'. One of '{TRADESTRD}' is expected."

      status(result) mustEqual BAD_REQUEST
      contentAsString(result) mustEqual expectedMessage
    }

    "must return BadRequest when passed in an empty request" in {
      val validateMessage = app.injector.instanceOf[ValidateArrivalNotificationAction]
      val cc              = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] =
        FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), NodeSeq.Empty)

      val result = controller.post()(req)

      status(result) mustEqual BAD_REQUEST
      contentAsString(result) mustEqual XmlError.RequestBodyEmptyMessage
    }

    "must return BadRequest when passed in incorrect request body" in {
      val validateMessage = app.injector.instanceOf[ValidateArrivalNotificationAction]
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
      val validateMessage = app.injector.instanceOf[ValidateArrivalNotificationAction]
      val cc              = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] =
        FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), <CC008A></CC008A>)

      val result = controller.post()(req)

      status(result) mustEqual BAD_REQUEST
    }
  }
}
