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

package v2.controllers.actions

import config.AppConfig
import data.TestXml
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.http.HttpVerbs
import play.api.mvc._
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import v2.base.TestActorSystem
import v2.controllers.actions.providers.MessageSizeActionProviderImpl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.NodeSeq

class MessageSizeActionSpec extends AnyFreeSpec with Matchers with ScalaFutures with MockitoSugar with TestXml with TestActorSystem {

  class Harness(messageSizeAction: MessageSizeAction[Request], cc: ControllerComponents) extends BackendController(cc) {

    def post: Action[NodeSeq] = (DefaultActionBuilder.apply(cc.parsers.anyContent) andThen messageSizeAction).async(cc.parsers.xml) {
      _ =>
        Future.successful(Ok)
    }

    def postContent: Action[AnyContent] =
      (DefaultActionBuilder.apply(cc.parsers.anyContent) andThen messageSizeAction).async {
        _ =>
          Future.successful(Ok)
      }
  }

  val appConfig: AppConfig = mock[AppConfig]
  when(appConfig.messageSizeLimit).thenReturn(500000)

  val messageSizeAction        = new MessageSizeActionProviderImpl(appConfig)
  val cc: ControllerComponents = Helpers.stubControllerComponents()

  "MessageSizeAction " - {

    "must allow a POST under 0.5mb" in {

      val controller                = new Harness(messageSizeAction(), cc)
      val req: FakeRequest[NodeSeq] = FakeRequest(method = HttpVerbs.POST, uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_LENGTH -> "123")), CC044A)
      val result                    = controller.post()(req)

      status(result) mustEqual OK
    }

    "must reject a POST over 0.5mb" in {
      val controller                = new Harness(messageSizeAction(), cc)
      val req: FakeRequest[NodeSeq] = FakeRequest(method = HttpVerbs.POST, uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_LENGTH -> "500001")), CC044A)
      val result                    = controller.post()(req)

      status(result) mustEqual REQUEST_ENTITY_TOO_LARGE

      val jsonResult = contentAsJson(result)
      (jsonResult \ "message").as[String] matches "Your message size must be less than [0-9]* bytes" mustBe true
      (jsonResult \ "code").as[String] mustEqual "REQUEST_ENTITY_TOO_LARGE"
    }

    "must reject a PUT over 0.5mb" in {
      val controller                = new Harness(messageSizeAction(), cc)
      val req: FakeRequest[NodeSeq] = FakeRequest(method = HttpVerbs.PUT, uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_LENGTH -> "500001")), CC044A)
      val result                    = controller.post()(req)

      status(result) mustEqual REQUEST_ENTITY_TOO_LARGE
    }

    "must NOT allow a POST if content-length header is missing" in {
      val controller                = new Harness(messageSizeAction(), cc)
      val req: FakeRequest[NodeSeq] = FakeRequest(method = HttpVerbs.POST, uri = "", headers = FakeHeaders(Seq()), CC044A)
      val result                    = controller.post()(req)

      status(result) mustEqual BAD_REQUEST
      val jsonResult = contentAsJson(result)
      (jsonResult \ "message").as[String] mustEqual "Missing content-length header"
      (jsonResult \ "code").as[String] mustEqual "BAD_REQUEST"
    }

    "must NOT allow a POST if content-length header is invalid" in {
      val controller                = new Harness(messageSizeAction(), cc)
      val req: FakeRequest[NodeSeq] = FakeRequest(method = HttpVerbs.POST, uri = "", headers = FakeHeaders(Seq(HeaderNames.CONTENT_LENGTH -> "ABC")), CC044A)
      val result                    = controller.post()(req)

      status(result) mustEqual BAD_REQUEST
      val jsonResult = contentAsJson(result)
      (jsonResult \ "message").as[String] mustEqual "Invalid content-length value"
      (jsonResult \ "code").as[String] mustEqual "BAD_REQUEST"
    }
  }
}
