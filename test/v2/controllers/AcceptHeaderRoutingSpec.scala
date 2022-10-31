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

package v2.controllers

import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.Accepted
import play.api.mvc.Results.NotAcceptable
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import routing.VersionedRouting
import v2.base.TestActorSystem
import v2.models.errors.PresentationError

import scala.concurrent.Future

class AcceptHeaderRoutingSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with ScalaCheckDrivenPropertyChecks
    with TestActorSystem {

  object Harness extends AcceptHeaderRouting

  val routes: PartialFunction[Option[String], Future[Result]] = {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON) | Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML) =>
      Future.successful(Accepted)
  }

  "must return expected status when given a valid accept header value" in {
    for (acceptHeaderVal <- Seq(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML)) {

      implicit val request = FakeRequest(method = "GET", "/test", FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeaderVal)), body = "")

      whenReady(Harness.acceptHeaderRoute(routes)) {
        result => result mustBe Accepted
      }

    }
  }

  "must return Not Acceptable when given an invalid accept header value" in {
    val invalidAcceptHeader = "application/vnd.hmrc.2.0+text"
    implicit val request    = FakeRequest(method = "GET", "/test", FakeHeaders(Seq(HeaderNames.ACCEPT -> invalidAcceptHeader)), body = "")

    whenReady(Harness.acceptHeaderRoute(routes)) {
      result =>
        result mustBe NotAcceptable(
          Json.toJson(
            PresentationError.notAcceptableError(
              s"Accept header $invalidAcceptHeader is not supported!"
            )
          )
        )
    }
  }

  "must return Not Acceptable when an accept header is not provided" in {
    implicit val request = FakeRequest(method = "GET", "/test", FakeHeaders(Seq.empty), body = "")

    whenReady(Harness.acceptHeaderRoute(routes)) {
      result =>
        result mustBe NotAcceptable(
          Json.toJson(
            PresentationError.notAcceptableError(
              "Accept header is required!"
            )
          )
        )
    }
  }

}
