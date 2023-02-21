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
import play.api.http.Status.NOT_ACCEPTABLE
import play.api.http.Status.OK
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.DefaultActionBuilder
import play.api.mvc.Request
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.Helpers.status
import routing.VersionedRouting
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import v2.base.TestActorSystem
import v2.controllers.actions.providers.AcceptHeaderActionProviderImpl

import scala.concurrent.Future

class AcceptHeaderActionSpec extends AnyFreeSpec with Matchers with ScalaFutures with MockitoSugar with TestXml with TestActorSystem {

  implicit val ec = materializer.executionContext

  class Harness(acceptHeaderAction: AcceptHeaderAction[Request], cc: ControllerComponents) extends BackendController(cc) {

    def post: Action[AnyContent] = (DefaultActionBuilder.apply(cc.parsers.anyContent) andThen acceptHeaderAction).async {
      _ =>
        Future.successful(Ok)
    }
  }

  val appConfig: AppConfig = mock[AppConfig]
  when(appConfig.messageSizeLimit).thenReturn(500000)

  val acceptHeaderAction       = new AcceptHeaderActionProviderImpl()
  val cc: ControllerComponents = Helpers.stubControllerComponents()

  "AcceptHeaderAction " - {

    for (
      acceptHeader <- Seq(
        VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON,
        VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML,
        VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN
      )
    )
      s"must allow valid accept header $acceptHeader" in {
        val controller = new Harness(acceptHeaderAction(acceptOnlyJson = false), cc)
        val req: FakeRequest[AnyContent] = FakeRequest(
          method = HttpVerbs.GET,
          uri = "",
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeader)),
          AnyContent.apply()
        )
        val result = controller.post()(req)

        status(result) mustEqual OK
      }

    s"must allow only json as valid accept header $VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON " in {
      val controller = new Harness(acceptHeaderAction(acceptOnlyJson = true), cc)
      val req: FakeRequest[AnyContent] = FakeRequest(
        method = HttpVerbs.GET,
        uri = "",
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)),
        AnyContent.apply()
      )
      val result = controller.post()(req)

      status(result) mustEqual OK
    }

    "must not allow other accept headers when acceptOnlyJson set to true " in {
      val controller = new Harness(acceptHeaderAction(acceptOnlyJson = true), cc)
      val req: FakeRequest[AnyContent] = FakeRequest(
        method = HttpVerbs.GET,
        uri = "",
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN)),
        AnyContent.apply()
      )
      val result = controller.post()(req)

      status(result) mustEqual NOT_ACCEPTABLE
    }

    for (
      acceptOnlyJson <- Seq(
        true,
        false
      )
    )
      s"must return invalid accept header when flag is set to $acceptOnlyJson" in {
        val controller = new Harness(acceptHeaderAction(acceptOnlyJson), cc)
        val req: FakeRequest[AnyContent] = FakeRequest(
          method = HttpVerbs.GET,
          uri = "",
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+text")),
          AnyContent.apply()
        )
        val result = controller.post()(req)

        status(result) mustEqual NOT_ACCEPTABLE
      }

    "must reject request without an accept header" in {
      val controller = new Harness(acceptHeaderAction(acceptOnlyJson = true), cc)
      val req: FakeRequest[AnyContent] = FakeRequest(
        method = HttpVerbs.GET,
        uri = "",
        headers = FakeHeaders(Seq.empty),
        AnyContent.apply()
      )
      val result = controller.post()(req)

      status(result) mustEqual NOT_ACCEPTABLE
    }
  }
}
