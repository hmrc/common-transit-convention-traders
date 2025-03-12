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

package routing

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout
import config.AppConfig
import org.mockito.Mockito.when
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.http.Status.NOT_ACCEPTABLE
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
import v2_1.fakes.controllers.FakeV2MovementsController
import v2_1.fakes.controllers.FakeV2TransitionalMovementsController

import scala.concurrent.duration.DurationInt
import scala.math.abs

class ArrivalsRouterSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar with TestActorSystem {

  implicit private val timeout: Timeout = 5.seconds

  val mockAppConfig = mock[AppConfig]

  val sut = new ArrivalsRouter(
    stubControllerComponents(),
    new FakeV2TransitionalMovementsController(),
    new FakeV2MovementsController(),
    mockAppConfig
  )

  val id = Gen.long
    .map {
      l =>
        f"${BigInt(abs(l))}%016x"
    }
    .sample
    .get

  "route to the version 2 controller" - {
    def executeTest(callValue: Call, sutValue: => Action[Source[ByteString, ?]], expectedStatus: Int, isVersion: Boolean) =
      Seq(
        Some(VERSION_2_ACCEPT_HEADER_VALUE_JSON.value),
        Some(VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML.value),
        Some(VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.value)
      ).foreach {
        acceptHeaderValue =>
          val arrivalsHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          )

          s"when the accept header equals ${acceptHeaderValue.getOrElse("nothing")}, it returns status code $expectedStatus" in {
            when(mockAppConfig.phase5TransitionalEnabled).thenReturn(isVersion)

            val request =
              FakeRequest(method = callValue.method, uri = callValue.url, body = <test></test>, headers = arrivalsHeaders)
            val result = call(sutValue, request)

            status(result) mustBe expectedStatus

            if (isVersion) {
              contentAsJson(result) mustBe Json.obj("version" -> 2) // ensure we get the unique value to verify we called the fake action
            } else {
              contentAsJson(result) mustBe Json.obj(
                "message" -> "CTC Traders API version 2.0 is no longer available. Use CTC Traders API v2.1 to submit transit messages.",
                "code"    -> "NOT_ACCEPTABLE"
              )
            }
          }
      }

    "when creating an arrival notification" - executeTest(routes.ArrivalsRouter.createArrivalNotification(), sut.createArrivalNotification(), ACCEPTED, true)

    "when creating an arrival notification return NOT_ACCEPTABLE when phase5 feature is disabled" - executeTest(
      routes.ArrivalsRouter.createArrivalNotification(),
      sut.createArrivalNotification(),
      NOT_ACCEPTABLE,
      false
    )

    "when getting an arrival" - executeTest(routes.ArrivalsRouter.getArrival(id), sut.getArrival(id), OK, true)

    "when getting an arrival return NOT_ACCEPTABLE when phase5 feature is disabled" - executeTest(
      routes.ArrivalsRouter.getArrival(id),
      sut.getArrival(id),
      NOT_ACCEPTABLE,
      false
    )

    "when getting arrivals for a given enrolment EORI" - executeTest(routes.ArrivalsRouter.getArrivalsForEori(), sut.getArrivalsForEori(), OK, true)

    "when getting arrivals for a given enrolment EORI return NOT_ACCEPTABLE when phase5 feature is disabled" - executeTest(
      routes.ArrivalsRouter.getArrivalsForEori(),
      sut.getArrivalsForEori(),
      NOT_ACCEPTABLE,
      false
    )

    "when getting a list of arrival messages with given arrivalId" - executeTest(routes.ArrivalsRouter.getArrival(id), sut.getArrival(id), OK, true)

    "when getting a single arrival message" - executeTest(routes.ArrivalsRouter.getArrivalMessage(id, id), sut.getArrivalMessage(id, id), OK, true)

    "when getting a single arrival message return NOT_ACCEPTABLE when phase5 feature is disabled" - executeTest(
      routes.ArrivalsRouter.getArrivalMessage(id, id),
      sut.getArrivalMessage(id, id),
      NOT_ACCEPTABLE,
      false
    )

    "when submitting a new message for an existing arrival" - executeTest(routes.ArrivalsRouter.attachMessage(id), sut.attachMessage(id), ACCEPTED, true)

    "when submitting a new message for an existing arrival return NOT_ACCEPTABLE when phase5 feature is disabled" - executeTest(
      routes.ArrivalsRouter.attachMessage(id),
      sut.attachMessage(id),
      NOT_ACCEPTABLE,
      false
    )

    "when getting messages for an existing arrival" - executeTest(routes.ArrivalsRouter.getArrivalMessageIds(id), sut.attachMessage(id), ACCEPTED, true)

    "when getting messages for an existing arrival return NOT_ACCEPTABLE when phase5 feature is disabled" - executeTest(
      routes.ArrivalsRouter.getArrivalMessageIds(id),
      sut.attachMessage(id),
      NOT_ACCEPTABLE,
      false
    )
  }

  "route to the version 2_1 controller" - {
    def executeTest(callValue: Call, sutValue: => Action[Source[ByteString, ?]], expectedStatus: Int, isVersion: Boolean) =
      Seq(
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML.value),
        Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.value)
      ).foreach {
        acceptHeaderValue =>
          val arrivalsHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> acceptHeaderValue.get, HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          )

          s"when the accept header equals ${acceptHeaderValue.getOrElse("nothing")}, it returns status code $expectedStatus" in {
            when(mockAppConfig.phase5FinalEnabled).thenReturn(isVersion)

            val request =
              FakeRequest(method = callValue.method, uri = callValue.url, body = <test></test>, headers = arrivalsHeaders)
            val result = call(sutValue, request)

            status(result) mustBe expectedStatus

            if (isVersion) {
              contentAsJson(result) mustBe Json.obj("version" -> 2.1) // ensure we get the unique value to verify we called the fake action
            } else {
              contentAsJson(result) mustBe Json.obj(
                "message" -> "CTC Traders API version 2.0 is no longer available. Use CTC Traders API v2.1 to submit transit messages.",
                "code"    -> "NOT_ACCEPTABLE"
              )
            }
          }
      }

    "when creating an arrival notification" - executeTest(routes.ArrivalsRouter.createArrivalNotification(), sut.createArrivalNotification(), ACCEPTED, true)

    "when getting an arrival" - executeTest(routes.ArrivalsRouter.getArrival(id), sut.getArrival(id), OK, true)

    "when getting arrivals for a given enrolment EORI" - executeTest(routes.ArrivalsRouter.getArrivalsForEori(), sut.getArrivalsForEori(), OK, true)

    "when getting a list of arrival messages with given arrivalId" - executeTest(routes.ArrivalsRouter.getArrival(id), sut.getArrival(id), OK, true)

    "when getting a single arrival message" - executeTest(routes.ArrivalsRouter.getArrivalMessage(id, id), sut.getArrivalMessage(id, id), OK, true)

    "when submitting a new message for an existing arrival" - executeTest(routes.ArrivalsRouter.attachMessage(id), sut.attachMessage(id), ACCEPTED, true)

    "when getting messages for an existing arrival" - executeTest(routes.ArrivalsRouter.getArrivalMessageIds(id), sut.attachMessage(id), ACCEPTED, true)
  }
}
