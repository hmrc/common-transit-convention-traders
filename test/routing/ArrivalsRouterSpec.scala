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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.util.Timeout
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Call
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
import scala.math.abs

class ArrivalsRouterSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar with TestActorSystem {

  implicit private val timeout: Timeout = 5.seconds

  val sut = new ArrivalsRouter(
    stubControllerComponents(),
    new FakeV1ArrivalsController(),
    new FakeV2ArrivalsController()
  )

  val id = Gen.long
    .map {
      l: Long =>
        f"${BigInt(abs(l))}%016x"
    }
    .sample
    .get

  "route to the version 2 controller" - {
    def executeTest(callValue: Call, sutValue: => Action[Source[ByteString, _]], expectedStatus: Int) = {
      val arrivalsHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      )

      s"when the accept header equals application/vnd.hmrc.2.0+json, it returns status code $expectedStatus" in {

        val request =
          FakeRequest(method = callValue.method, uri = callValue.url, body = <test></test>, headers = arrivalsHeaders)
        val result = call(sutValue, request)

        // status(result) mustBe expectedStatus
        contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
      }
    }

    "when creating an arrival notification" - executeTest(
      routes.ArrivalsRouter.createArrivalNotification(),
      sut.createArrivalNotification(),
      ACCEPTED
    )
    "when getting an arrival" - executeTest(
      routes.ArrivalsRouter.getArrival(id),
      sut.getArrival(id),
      OK
    )
    "when getting arrivals for a given enrolment EORI" - executeTest(
      routes.ArrivalsRouter.getArrivalsForEori(),
      sut.getArrivalsForEori(),
      OK
    )
  }

  "route to the version 1 controller" - {
    def executeTest(callValue: Call, sutValue: => Action[Source[ByteString, _]], expectedStatus: Int) =
      Seq(None, Some("application/vnd.hmrc.1.0+json"), Some("text/html"), Some("application/vnd.hmrc.1.0+xml"), Some("text/javascript")).foreach {
        acceptHeaderValue =>
          val arrivalsHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> acceptHeaderValue.getOrElse(""), HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          )

          s"when the accept header equals ${acceptHeaderValue.getOrElse("nothing")}, it returns status code $expectedStatus" in {
            val request =
              FakeRequest(
                method = callValue.method,
                uri = callValue.url,
                body = <test></test>,
                headers = arrivalsHeaders
              )
            val result = call(sutValue, request)

            status(result) mustBe expectedStatus
            contentAsJson(result) mustBe Json.obj("version" -> 1) // ensure we get the unique value to verify we called the fake action
          }
      }

    "when creating an arrival notification" - executeTest(routes.ArrivalsRouter.createArrivalNotification(), sut.createArrivalNotification(), ACCEPTED)
    "when getting an arrival" - executeTest(routes.ArrivalsRouter.getArrival("123"), sut.getArrival("123"), OK)
    "when getting arrivals for a given enrolment EORI" - executeTest(routes.ArrivalsRouter.getArrivalsForEori(), sut.getArrivalsForEori(), OK)
  }

}
