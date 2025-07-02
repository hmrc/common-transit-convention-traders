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

import models.common.MovementType.Arrival
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout
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
import v2_1.base.TestActorSystem
import v2_1.fakes.controllers.FakeMovementsController

import scala.concurrent.duration.DurationInt
import scala.math.abs

class ArrivalsRouterSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar with TestActorSystem {

  implicit private val timeout: Timeout = 5.seconds

  val sut = new GenericRouting(
    stubControllerComponents(),
    new FakeMovementsController()
  )

  val id = Gen.long
    .map {
      l =>
        f"${BigInt(abs(l))}%016x"
    }
    .sample
    .get

  "route to the version 2_1 controller" - {
    def executeTest(callValue: Call, sutValue: => Action[Source[ByteString, ?]], expectedStatus: Int, isVersion: Boolean): Unit =
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
    "when creating an arrival notification" - executeTest(routes.GenericRouting.createMovement(Arrival), sut.createMovement(Arrival), ACCEPTED, true)

    "when getting an arrival" - executeTest(routes.GenericRouting.getMovement(Arrival, id), sut.getMovement(Arrival, id), OK, true)

    "when getting arrivals for a given enrolment EORI" - executeTest(
      routes.GenericRouting.getMovementForEori(movementType = Arrival),
      sut.getMovementForEori(movementType = Arrival),
      OK,
      true
    )

    "when getting a list of arrival messages with given arrivalId" - executeTest(
      routes.GenericRouting.getMovement(Arrival, id),
      sut.getMovement(Arrival, id),
      OK,
      true
    )

    "when getting a single arrival message" - executeTest(routes.GenericRouting.getMessage(Arrival, id, id), sut.getMessage(Arrival, id, id), OK, true)

    "when submitting a new message for an existing arrival" - executeTest(
      routes.GenericRouting.attachMessage(Arrival, id),
      sut.attachMessage(Arrival, id),
      ACCEPTED,
      true
    )

    "when getting messages for an existing arrival" - executeTest(
      routes.GenericRouting.getMessageIds(Arrival, id),
      sut.attachMessage(Arrival, id),
      ACCEPTED,
      true
    )
  }
}
