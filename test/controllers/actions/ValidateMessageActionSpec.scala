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
import play.api.mvc.{Action, ControllerComponents, DefaultActionBuilder}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.test.Helpers._
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.NodeSeq

class ValidateMessageActionSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach with TestXml {
  override lazy val app = GuiceApplicationBuilder()
    .overrides(bind[AuthAction].to[FakeAuthAction])
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  class Harness(validateMessage: ValidateMessageAction, cc: ControllerComponents) extends BackendController(cc) {
    def post: Action[NodeSeq] = (DefaultActionBuilder.apply(cc.parsers.anyContent) andThen validateMessage).async(cc.parsers.xml) {
      _ =>
        Future.successful(Ok)
    }
  }

  "ValidateMessageAction" - {
    "must execute the block when passed in a valid IE044 xml request" in {
      val validateMessage = app.injector.instanceOf[ValidateMessageAction]
      val cc = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), CC044A)

      val result = controller.post()(req)

      status(result) mustEqual OK
    }

    "must return BadRequest when passed in an invalid IE044 xml request " in {
      val validateMessage = app.injector.instanceOf[ValidateMessageAction]
      val cc = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), InvalidCC044A)

      val result = controller.post()(req)

      status(result) mustEqual BAD_REQUEST
    }

    "must return BadRequest when passed in an empty request" in {
      val validateMessage = app.injector.instanceOf[ValidateMessageAction]
      val cc = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), NodeSeq.Empty)

      val result = controller.post()(req)

      status(result) mustEqual BAD_REQUEST
    }

    "must return NotImplemented when passed in an incorrect XML request" in {
      val validateMessage = app.injector.instanceOf[ValidateMessageAction]
      val cc = app.injector.instanceOf[ControllerComponents]

      val controller = new Harness(validateMessage, cc)

      val req: FakeRequest[NodeSeq] = FakeRequest(method = "", uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), <CC045A></CC045A>)

      val result = controller.post()(req)

      status(result) mustEqual NOT_IMPLEMENTED
    }
  }
}
