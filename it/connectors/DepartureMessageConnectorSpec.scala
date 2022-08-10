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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import config.Constants
import controllers.routes
import models.domain._
import models.response.HateoasResponseDeparture
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.Helpers.BAD_REQUEST
import play.api.test.Helpers.INTERNAL_SERVER_ERROR
import play.api.test.Helpers.NOT_FOUND
import play.api.test.Helpers.OK
import uk.gov.hmrc.http.Authorization
import uk.gov.hmrc.http.HeaderCarrier
import utils.CallOps._

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext.Implicits.global

class DepartureMessageConnectorSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with utils.GuiceWiremockSuite
    with ScalaFutures
    with IntegrationPatience
    with ScalaCheckPropertyChecks {

  "get" - {
    "must return MovementMessage when message is found" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]

      val movement = MovementMessage(
        routing.routes.DeparturesRouter.getMessage(DepartureId(1).toString, MessageId(1).toString).urlWithContext,
        LocalDateTime.now,
        "abc",
        <test>default</test>
      )

      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages/1")
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("a5sesqerTyi135/"))
          .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
          .withHeader(Constants.ChannelHeader, equalTo("api"))
          .withHeader(Constants.XClientIdHeader, equalTo("foo"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(movement).toString())
          )
      )

      implicit val hc = HeaderCarrier()
        .copy(authorization = Some(Authorization("a5sesqerTyi135/")))
        .withExtraHeaders(Constants.XClientIdHeader -> "foo")

      val result = connector.get(DepartureId(1), MessageId(1)).futureValue

      result mustEqual Right(movement)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      val departure = Departure(
        DepartureId(1),
        routes.DeparturesController.getDeparture(DepartureId(1)).urlWithContext,
        routing.routes.DeparturesRouter.getMessageIds("1").urlWithContext,
        Some("MRN"),
        LocalDateTime.now,
        LocalDateTime.now
      )

      val response = HateoasResponseDeparture(departure)
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages/1")
        ).willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(response).toString())
        )
      )

      implicit val hc = HeaderCarrier()

      val result = connector.get(DepartureId(1), MessageId(1)).futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual INTERNAL_SERVER_ERROR
      }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages/1")
        ).willReturn(aResponse().withStatus(NOT_FOUND))
      )

      implicit val hc = HeaderCarrier()

      val result = connector.get(DepartureId(1), MessageId(1)).futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual NOT_FOUND
      }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages/1")
        ).willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      implicit val hc = HeaderCarrier()

      val result = connector.get(DepartureId(1), MessageId(1)).futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual BAD_REQUEST
      }
    }

    "must return HttpResponse with an internal server if if there is an internal server error" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages/1")
        ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      implicit val hc = HeaderCarrier()

      val result = connector.get(DepartureId(1), MessageId(1)).futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual INTERNAL_SERVER_ERROR
      }
    }
  }

  "getDepartureMessages" - {
    "must return Departure when departure is found" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      val departure = DepartureWithMessages(
        DepartureId(1),
        routes.DeparturesController.getDeparture(DepartureId(1)).urlWithContext,
        routing.routes.DeparturesRouter.getMessageIds("1").urlWithContext,
        Some("MRN"),
        LocalDateTime.now,
        LocalDateTime.now,
        Seq(
          MovementMessage(
            routing.routes.DeparturesRouter.getMessage(DepartureId(1).toString, MessageId(1).toString).urlWithContext,
            LocalDateTime.now,
            "abc",
            <test>default</test>
          ),
          MovementMessage(
            routing.routes.DeparturesRouter.getMessage(DepartureId(1).toString, MessageId(2).toString).urlWithContext,
            LocalDateTime.now,
            "abc",
            <test>default</test>
          )
        )
      )

      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages")
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("a5sesqerTyi135/"))
          .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
          .withHeader(Constants.ChannelHeader, equalTo("api"))
          .withHeader(Constants.XClientIdHeader, equalTo("foo"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(departure).toString())
          )
      )

      implicit val hc = HeaderCarrier()
        .copy(authorization = Some(Authorization("a5sesqerTyi135/")))
        .withExtraHeaders(Constants.XClientIdHeader -> "foo")

      val result = connector.getMessages(DepartureId(1), receivedSince = None).futureValue

      result mustEqual Right(departure)
    }

    "must render receivedSince parameter into request URL" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      val dateTime  = Some(OffsetDateTime.of(2021, 3, 14, 13, 15, 30, 0, ZoneOffset.ofHours(1)))
      val departure = DepartureWithMessages(
        DepartureId(1),
        routes.DeparturesController.getDeparture(DepartureId(1)).urlWithContext,
        routing.routes.DeparturesRouter.getMessageIds("1").urlWithContext,
        Some("MRN"),
        LocalDateTime.now,
        LocalDateTime.now,
        Seq(
          MovementMessage(
            routing.routes.DeparturesRouter.getMessage(DepartureId(1).toString, MessageId(1).toString).urlWithContext,
            LocalDateTime.now,
            "abc",
            <test>default</test>
          ),
          MovementMessage(
            routing.routes.DeparturesRouter.getMessage(DepartureId(1).toString, MessageId(2).toString).urlWithContext,
            LocalDateTime.now,
            "abc",
            <test>default</test>
          )
        )
      )

      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages?receivedSince=2021-03-14T13%3A15%3A30%2B01%3A00")
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("a5sesqerTyi135/"))
          .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
          .withHeader(Constants.ChannelHeader, equalTo("api"))
          .withHeader(Constants.XClientIdHeader, equalTo("foo"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(departure).toString())
          )
      )

      implicit val hc = HeaderCarrier()
        .copy(authorization = Some(Authorization("a5sesqerTyi135/")))
        .withExtraHeaders(Constants.XClientIdHeader -> "foo")

      val result = connector.getMessages(DepartureId(1), receivedSince = dateTime).futureValue

      result mustEqual Right(departure)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      val departure = Departure(
        DepartureId(1),
        routes.DeparturesController.getDeparture(DepartureId(1)).urlWithContext,
        routing.routes.DeparturesRouter.getMessageIds("1").urlWithContext,
        Some("MRN"),
        LocalDateTime.now,
        LocalDateTime.now
      )

      val response = HateoasResponseDeparture(departure)
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages")
        ).willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(response).toString())
        )
      )

      implicit val hc = HeaderCarrier()

      val result = connector.getMessages(DepartureId(1), receivedSince = None).futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual INTERNAL_SERVER_ERROR
      }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages")
        ).willReturn(aResponse().withStatus(NOT_FOUND))
      )

      implicit val hc = HeaderCarrier()

      val result = connector.getMessages(DepartureId(1), receivedSince = None).futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual NOT_FOUND
      }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages")
        ).willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      implicit val hc = HeaderCarrier()

      val result = connector.getMessages(DepartureId(1), receivedSince = None).futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual BAD_REQUEST
      }
    }

    "must return HttpResponse with an internal server if there is an internal server error" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages")
        ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      implicit val hc = HeaderCarrier()

      val result = connector.getMessages(DepartureId(1), receivedSince = None).futureValue

      result.isLeft mustEqual true
      result.left.map {
        x => x.status mustEqual INTERNAL_SERVER_ERROR
      }
    }
  }

  //TODO: Refactor this and other spec usages to a common trait
  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transits-movements-trader-at-departure.port")
}
