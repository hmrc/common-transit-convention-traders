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
import models.Box
import models.BoxId
import models.domain.{Arrival, ArrivalId, Arrivals}
import models.response.HateoasResponseArrival
import models.response.HateoasResponseArrivals
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import utils.CallOps._
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

import scala.concurrent.ExecutionContext.Implicits.global

class ArrivalConnectorSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with utils.WiremockSuite with ScalaFutures with IntegrationPatience with ScalaCheckPropertyChecks {
  val testBoxId   = BoxId("testBoxId")
  val testBoxName = "testBoxName"
  val testBox     = Box(testBoxId, testBoxName)

  "post" - {
    "must return ACCEPTED when post is successful" in {
      val connector = app.injector.instanceOf[ArrivalConnector]

      server.stubFor(
        post(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals")
        ).willReturn(
          aResponse()
            .withStatus(ACCEPTED)
            .withBody(
              Json.stringify(
                Json.toJson(
                  Option(testBox)
                )
              )
            )
        )
      )

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.post("<document></document>").futureValue

      result.right.get.responseData mustEqual Option(testBox)
    }

    "must return INTERNAL_SERVER_ERROR when post" - {
      "returns INTERNAL_SERVER_ERROR" in {
        val connector = app.injector.instanceOf[ArrivalConnector]

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals")
          ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
        )

        implicit val hc = HeaderCarrier()
        implicit val requestHeader = FakeRequest()

        val result = connector.post("<document></document>").futureValue

        result.left.get.statusCode mustEqual INTERNAL_SERVER_ERROR      }

    }

    "must return BAD_REQUEST when post returns BAD_REQUEST" in {
      val connector = app.injector.instanceOf[ArrivalConnector]

      server.stubFor(
        post(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals")
        ).willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.post("<document></document>").futureValue

      result.left.get.statusCode mustEqual BAD_REQUEST
    }
  }

  "put" - {
    "must return ACCEPTED when put is successful" in {
        val connector = app.injector.instanceOf[ArrivalConnector]

        server.stubFor(
          put(
            urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/2")
          ).willReturn(
            aResponse()
              .withStatus(ACCEPTED)
              .withBody(
                Json.stringify(
                  Json.toJson(
                    Option(testBox)
                  )
                )
              )
          )
        )

        implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

        val result = connector.put("<document></document>", ArrivalId(2)).futureValue

      result.right.get.responseData mustEqual Option(testBox)
    }

    "must return INTERNAL_SERVER_ERROR when put" - {
      "returns INTERNAL_SERVER_ERROR" in {
        val connector = app.injector.instanceOf[ArrivalConnector]

        server.stubFor(
          put(
            urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/2")
          ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
        )

        implicit val hc = HeaderCarrier()
        implicit val requestHeader = FakeRequest()

        val result = connector.put("<document></document>", ArrivalId(2)).futureValue

        result.left.get.statusCode mustEqual INTERNAL_SERVER_ERROR
      }

    }

    "must return BAD_REQUEST when put returns BAD_REQUEST" in {
      val connector = app.injector.instanceOf[ArrivalConnector]

      server.stubFor(
        put(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/2")
        ).willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.put("<document></document>", ArrivalId(2)).futureValue

      result.left.get.statusCode mustEqual BAD_REQUEST
    }

  }

  "get" - {
    "must return Arrival when arrival is found" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      val arrival = Arrival(ArrivalId(1), routes.ArrivalMovementController.getArrival(ArrivalId(1)).urlWithContext, routes.ArrivalMessagesController.getArrivalMessages(ArrivalId(1)).urlWithContext, "MRN", "status", LocalDateTime.now, LocalDateTime.now)

      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1"))
        .willReturn(aResponse().withStatus(OK)
        .withBody(Json.toJson(arrival).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get(ArrivalId(1)).futureValue

      result mustEqual Right(arrival)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      val arrival = Arrival(ArrivalId(1), routes.ArrivalMovementController.getArrival(ArrivalId(1)).urlWithContext, routes.ArrivalMessagesController.getArrivalMessages(ArrivalId(1)).urlWithContext, "MRN", "status", LocalDateTime.now, LocalDateTime.now)

      val response = HateoasResponseArrival(arrival)

      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1"))
        .willReturn(aResponse().withStatus(OK)
        .withBody(Json.toJson(response).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get(ArrivalId(1)).futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1"))
        .willReturn(aResponse().withStatus(NOT_FOUND)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get(ArrivalId(1)).futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual NOT_FOUND }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1"))
        .willReturn(aResponse().withStatus(BAD_REQUEST)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get(ArrivalId(1)).futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual BAD_REQUEST }
    }

    "must return HttpResponse with an internal server if there is an internal server error" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1"))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get(ArrivalId(1)).futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }
  }

  "getForEori" - {
    "must return Arrival when arrival is found" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      val arrivals = Arrivals(Seq(Arrival(ArrivalId(1), routes.ArrivalMovementController.getArrival(ArrivalId(1)).urlWithContext, routes.ArrivalMessagesController.getArrivalMessages(ArrivalId(1)).urlWithContext, "MRN", "status", LocalDateTime.now, LocalDateTime.now)), 1, 1)

      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals"))
        .willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(arrivals).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori(None).futureValue

      result mustEqual Right(arrivals)
    }

    "must render updatedSince parameter into request URL" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      val arrivals = Arrivals(Seq(Arrival(ArrivalId(1), routes.ArrivalMovementController.getArrival(ArrivalId(1)).urlWithContext, routes.ArrivalMessagesController.getArrivalMessages(ArrivalId(1)).urlWithContext, "MRN", "status", LocalDateTime.now, LocalDateTime.now)), 1, 1)
      val dateTime = Some(OffsetDateTime.of(2021, 3, 14, 13, 15, 30, 0, ZoneOffset.ofHours(1)))

      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals?updatedSince=2021-03-14T13%3A15%3A30%2B01%3A00"))
        .willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(arrivals).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori(dateTime).futureValue

      result mustEqual Right(arrivals)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      val arrival = Arrivals(Seq(Arrival(ArrivalId(1), routes.ArrivalMovementController.getArrival(ArrivalId(1)).urlWithContext, routes.ArrivalMessagesController.getArrivalMessages(ArrivalId(1)).urlWithContext, "MRN", "status", LocalDateTime.now, LocalDateTime.now)), 1, 1)

      val response = HateoasResponseArrivals(arrival)

      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals"))
        .willReturn(aResponse().withStatus(OK)
        .withBody(Json.toJson(response).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori(None).futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals"))
        .willReturn(aResponse().withStatus(NOT_FOUND)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori(None).futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual NOT_FOUND }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals"))
        .willReturn(aResponse().withStatus(BAD_REQUEST)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori(None).futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual BAD_REQUEST }
    }

    "must return HttpResponse with an internal server if there is an internal server error" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals"))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori(None).futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }

  }

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movement-trader-at-destination.port")
}
