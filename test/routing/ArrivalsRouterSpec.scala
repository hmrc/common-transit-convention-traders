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

package routing

import akka.util.Timeout
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.http.Status.ACCEPTED
import play.api.libs.json.Json
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.call
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import v2.base.TestActorSystem
import v2.fakes.controllers.FakeV1ArrivalsController
import v2.fakes.controllers.FakeV2ArrivalsController

import scala.concurrent.duration.DurationInt

class ArrivalsRouterSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar with TestActorSystem {

  implicit private val timeout: Timeout = 5.seconds

  val sut = new ArrivalsRouter(
    stubControllerComponents(),
    new FakeV1ArrivalsController(),
    new FakeV2ArrivalsController()
  )

  for (
    endpoint <- Seq(
      routes.ArrivalsRouter.createArrivalNotification(),
      routes.ArrivalsRouter.getArrival("123"),
      routes.ArrivalsRouter.getArrivalsForEori()
    )
  )
    s"${endpoint.method} ${endpoint.url}" - {
      "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {

        val arrivalsHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml")
        )

        "must route to the v2 controller and return Accepted when successful" in {

          val request =
            FakeRequest(method = "POST", uri = endpoint.url, body = <test></test>, headers = arrivalsHeaders)
          val result = call(sut.createArrivalNotification(), request)

          status(result) mustBe ACCEPTED
          contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
        }

      }

      Seq(None, Some("application/vnd.hmrc.1.0+json"), Some("text/html"), Some("application/vnd.hmrc.1.0+xml"), Some("text/javascript")).foreach {
        acceptHeaderValue =>
          val acceptHeader = acceptHeaderValue
            .map(
              header => Seq(HeaderNames.ACCEPT -> header)
            )
            .getOrElse(Seq.empty)
          val arrivalHeaders = FakeHeaders(acceptHeader ++ Seq(HeaderNames.CONTENT_TYPE -> "application/xml"))
          val withString = acceptHeaderValue
            .getOrElse("nothing")
          s"with accept header set to $withString" - {

            "must route to the v1 controller and return Accepted when successful" in {

              val request =
                FakeRequest(method = "POST", uri = routes.ArrivalsRouter.createArrivalNotification().url, body = <test></test>, headers = arrivalHeaders)
              val result = call(sut.createArrivalNotification(), request)

              status(result) mustBe ACCEPTED
              contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
            }
          }

      }
    }
}
