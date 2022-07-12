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

package controllers

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import models.domain.Departures
import models.response.HateoasResponseDepartures
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

class CustomJsonErrorHandlerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneServerPerSuite
    with utils.GuiceWiremockSuite
    with ScalaFutures
    with IntegrationPatience {

  override protected def portConfigKey: Seq[String] = Seq(
    "microservice.services.auth.port",
    "microservice.services.transits-movements-trader-at-departure.port"
  )

  "CustomJsonErrorHandler" - {
    "return error message when a bad date format is sent" in {
      val emptyDepartures = Departures(Seq.empty, 0, 0)

      server.stubFor(
        post(urlEqualTo("/auth/authorise"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                """
                |{
                |  "authorisedEnrolments": [{
                |    "key": "HMCE-NCTS-ORG",
                |    "identifiers": [{
                |      "key": "VatRegNoTURN",
                |      "value": "1234567"
                |    }],
                |    "state": "Active"
                |  }]
                |}
                """.trim.stripMargin
              )
          )
      )

      server.stubFor(
        get(urlPathEqualTo("/transits-movements-trader-at-departure/movements/departures"))
          .willReturn(aResponse().withBody(Json.stringify(Json.toJson(emptyDepartures))).withStatus(OK))
      )

      val httpClient = app.injector.instanceOf[WSClient]
      val requestId  = UUID.randomUUID().toString
      val response = httpClient
        .url(s"http://localhost:$port/movements/departures?updatedSince=15-06-2021")
        .withHttpHeaders(
          "Accept"       -> "application/vnd.hmrc.1.0+json",
          "channel"      -> "api",
          "X-Request-ID" -> requestId
        )
        .get()
        .futureValue

      response.status shouldBe BAD_REQUEST

      response.json shouldBe Json.obj(
        "statusCode" -> BAD_REQUEST,
        "message"    -> "Cannot parse parameter updatedSince as a valid ISO 8601 timestamp, e.g. 2015-09-08T01:55:28+00:00"
      )
    }

    "return error message when the date is not URL encoded" in {
      val emptyDepartures = Departures(Seq.empty, 0, 0)

      server.stubFor(
        post(urlEqualTo("/auth/authorise"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                """
                |{
                |  "authorisedEnrolments": [{
                |    "key": "HMCE-NCTS-ORG",
                |    "identifiers": [{
                |      "key": "VatRegNoTURN",
                |      "value": "1234567"
                |    }],
                |    "state": "Active"
                |  }]
                |}
                """.trim.stripMargin
              )
          )
      )

      server.stubFor(
        get(urlPathEqualTo("/transits-movements-trader-at-departure/movements/departures"))
          .willReturn(aResponse().withBody(Json.stringify(Json.toJson(emptyDepartures))).withStatus(OK))
      )

      val httpClient = app.injector.instanceOf[WSClient]
      val requestId  = UUID.randomUUID().toString
      val response = httpClient
        .url(s"http://localhost:$port/movements/departures?updatedSince=2021-06-15T10:13:05+00:00")
        .withHttpHeaders(
          "Accept"       -> "application/vnd.hmrc.1.0+json",
          "channel"      -> "api",
          "X-Request-ID" -> requestId
        )
        .get()
        .futureValue

      response.status shouldBe BAD_REQUEST

      response.json shouldBe Json.obj(
        "statusCode" -> BAD_REQUEST,
        "message"    -> "Cannot parse parameter updatedSince as a valid ISO 8601 timestamp, e.g. 2015-09-08T01:55:28+00:00"
      )
    }

    "succeed when the date is in the correct format" in {
      val emptyDepartures = Departures(Seq.empty, 0, 0)

      server.stubFor(
        post(urlEqualTo("/auth/authorise"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                """
                |{
                |  "authorisedEnrolments": [{
                |    "key": "HMCE-NCTS-ORG",
                |    "identifiers": [{
                |      "key": "VatRegNoTURN",
                |      "value": "1234567"
                |    }],
                |    "state": "Active"
                |  }]
                |}
                """.trim.stripMargin
              )
          )
      )

      server.stubFor(
        get(urlPathEqualTo("/transits-movements-trader-at-departure/movements/departures"))
          .willReturn(aResponse().withBody(Json.stringify(Json.toJson(emptyDepartures))).withStatus(OK))
      )

      val httpClient = app.injector.instanceOf[WSClient]
      val requestId  = UUID.randomUUID().toString

      val response = httpClient
        .url(s"http://localhost:$port/movements/departures?updatedSince=2021-06-15T10%3A13%3A05%2B00%3A00")
        .withHttpHeaders(
          "Accept"        -> "application/vnd.hmrc.1.0+json",
          "channel"       -> "api",
          "X-Request-ID"  -> requestId,
          "Authorization" -> "Bearer token"
        )
        .get()
        .futureValue

      response.status shouldBe OK

      response.json shouldBe HateoasResponseDepartures(emptyDepartures)
    }
  }
}
