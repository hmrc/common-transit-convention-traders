/*
 * Copyright 2021 HM Revenue & Customs
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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import controllers.routes
import models.domain.Departure
import models.domain.Departures
import models.response.HateaosResponseDeparture
import models.response.HateaosResponseDepartures
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import utils.CallOps._

import java.time.LocalDateTime
import uk.gov.hmrc.http.UpstreamErrorResponse
import scala.concurrent.ExecutionContext.Implicits.global
import models.Box

class DepartureConnectorSpec extends AnyFreeSpec with Matchers with WiremockSuite with ScalaFutures with IntegrationPatience with ScalaCheckPropertyChecks {
  "post" - {

    "must return ACCEPTED when post is successful" in {
      val connector = app.injector.instanceOf[DeparturesConnector]

      server.stubFor(
        post(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures")
        ).willReturn(aResponse().withStatus(ACCEPTED))
      )

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.post("<document></document>").futureValue

      result mustEqual Right(Option.empty[Box])
    }

    "must return INTERNAL_SERVER_ERROR when post" - {
      "returns INTERNAL_SERVER_ERROR" in {
        val connector = app.injector.instanceOf[DeparturesConnector]

        server.stubFor(
          post(
            urlEqualTo("/transits-movements-trader-at-departure/movements/departures")
          ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
        )

        val errorMessage = "test error message"

        implicit val hc            = HeaderCarrier()
        implicit val requestHeader = FakeRequest().withBody(errorMessage)

        val result = connector.post("<document></document>").futureValue

        result mustEqual Left(UpstreamErrorResponse(errorMessage, INTERNAL_SERVER_ERROR))
      }

    }

    "must return BAD_REQUEST when post returns BAD_REQUEST" in {
      val connector = app.injector.instanceOf[DeparturesConnector]

      server.stubFor(
        post(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures")
        ).willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      val errorMessage = "test error message"

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest().withBody(errorMessage)

      val result = connector.post("<document></document>").futureValue

      result mustEqual Left(UpstreamErrorResponse(errorMessage, BAD_REQUEST))
    }
  }
  "get" - {
    "must return Departure when departure is found" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      val departure = Departure(
        1,
        routes.DeparturesController.getDeparture("1").urlWithContext,
        routes.DepartureMessagesController.getDepartureMessages("1").urlWithContext,
        Some("MRN"),
        "status",
        LocalDateTime.now,
        LocalDateTime.now
      )

      server.stubFor(
        get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(departure).toString())
          )
      )

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result mustEqual Right(departure)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      val departure = Departure(
        1,
        routes.DeparturesController.getDeparture("1").urlWithContext,
        routes.DepartureMessagesController.getDepartureMessages("1").urlWithContext,
        Some("MRN"),
        "status",
        LocalDateTime.now,
        LocalDateTime.now
      )

      val response = HateaosResponseDeparture(departure)

      server.stubFor(
        get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(response).toString())
          )
      )

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual INTERNAL_SERVER_ERROR
      }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(
        get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1"))
          .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual NOT_FOUND
      }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(
        get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1"))
          .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual BAD_REQUEST
      }
    }

    "must return HttpResponse with an internal server if there is an internal server error" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(
        get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1"))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual INTERNAL_SERVER_ERROR
      }
    }
  }

  "getForEori" - {
    "must return Departure when departure is found" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      val departures = Departures(
        Seq(
          Departure(
            1,
            routes.DeparturesController.getDeparture("1").urlWithContext,
            routes.DepartureMessagesController.getDepartureMessages("1").urlWithContext,
            Some("1"),
            "status",
            LocalDateTime.now,
            LocalDateTime.now
          )
        )
      )

      server.stubFor(
        get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(departures).toString())
          )
      )

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori.futureValue

      result mustEqual Right(departures)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      val departures = Departures(
        Seq(
          Departure(
            1,
            routes.DeparturesController.getDeparture("1").urlWithContext,
            routes.DepartureMessagesController.getDepartureMessages("1").urlWithContext,
            Some("1"),
            "status",
            LocalDateTime.now,
            LocalDateTime.now
          )
        )
      )

      val response = HateaosResponseDepartures(departures)

      server.stubFor(
        get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(response).toString())
          )
      )

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori.futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual INTERNAL_SERVER_ERROR
      }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(
        get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/"))
          .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori.futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual NOT_FOUND
      }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(
        get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/"))
          .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori.futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual BAD_REQUEST
      }
    }

    "must return HttpResponse with an internal server if there is an internal server error" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(
        get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/"))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      implicit val hc            = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori.futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual INTERNAL_SERVER_ERROR
      }
    }

  }

  override protected def portConfigKey: String = "microservice.services.transits-movements-trader-at-departure.port"
}
