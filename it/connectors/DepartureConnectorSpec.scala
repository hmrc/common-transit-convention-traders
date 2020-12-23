package connectors

import java.time.LocalDateTime
import com.github.tomakehurst.wiremock.client.WireMock._
import controllers.routes
import models.domain.{Departure, Departures}
import models.response.{HateaosResponseDeparture, HateaosResponseDepartures}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import utils.CallOps._

import scala.concurrent.ExecutionContext.Implicits.global

class DepartureConnectorSpec extends AnyFreeSpec with Matchers with WiremockSuite with ScalaFutures with IntegrationPatience with ScalaCheckPropertyChecks {
  "post" - {
    "must return ACCEPTED when post is successful" in {
      val connector = app.injector.instanceOf[DeparturesConnector]

      server.stubFor(
        post(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/")
        ).willReturn(aResponse().withStatus(ACCEPTED))
      )

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.post("<document></document>").futureValue

      result.status mustEqual ACCEPTED
    }

    "must return INTERNAL_SERVER_ERROR when post" - {
      "returns INTERNAL_SERVER_ERROR" in {
        val connector = app.injector.instanceOf[DeparturesConnector]

        server.stubFor(
          post(
            urlEqualTo("/transits-movements-trader-at-departure/movements/departures/")
          ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
        )

        implicit val hc = HeaderCarrier()
        implicit val requestHeader = FakeRequest()

        val result = connector.post("<document></document>").futureValue

        result.status mustEqual INTERNAL_SERVER_ERROR
      }

    }

    "must return BAD_REQUEST when post returns BAD_REQUEST" in {
      val connector = app.injector.instanceOf[DeparturesConnector]

      server.stubFor(
        post(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/")
        ).willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.post("<document></document>").futureValue

      result.status mustEqual BAD_REQUEST
    }
  }
  "get" - {
    "must return Departure when departure is found" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      val departure = Departure(1, routes.DeparturesController.getDeparture("1").urlWithContext, routes.DepartureMessagesController.getDepartureMessages("1").urlWithContext, Some("MRN"), "status", LocalDateTime.now, LocalDateTime.now)

      server.stubFor(get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1"))
        .willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(departure).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result mustEqual Right(departure)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      val departure = Departure(1, routes.DeparturesController.getDeparture("1").urlWithContext, routes.DepartureMessagesController.getDepartureMessages("1").urlWithContext, Some("MRN"), "status", LocalDateTime.now, LocalDateTime.now)

      val response = HateaosResponseDeparture(departure)

      server.stubFor(get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1"))
        .willReturn(aResponse().withStatus(OK)
        .withBody(Json.toJson(response).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1"))
        .willReturn(aResponse().withStatus(NOT_FOUND)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual NOT_FOUND }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1"))
        .willReturn(aResponse().withStatus(BAD_REQUEST)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual BAD_REQUEST }
    }

    "must return HttpResponse with an internal server if there is an internal server error" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1"))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }
  }

  "getForEori" - {
    "must return Departure when departure is found" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      val departures = Departures(Seq(Departure(1, routes.DeparturesController.getDeparture("1").urlWithContext, routes.DepartureMessagesController.getDepartureMessages("1").urlWithContext, Some("1"), "status", LocalDateTime.now, LocalDateTime.now)))

      server.stubFor(get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/"))
        .willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(departures).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori.futureValue

      result mustEqual Right(departures)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      val departures = Departures(Seq(Departure(1, routes.DeparturesController.getDeparture("1").urlWithContext, routes.DepartureMessagesController.getDepartureMessages("1").urlWithContext, Some("1"), "status", LocalDateTime.now, LocalDateTime.now)))

      val response = HateaosResponseDepartures(departures)

      server.stubFor(get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/"))
        .willReturn(aResponse().withStatus(OK)
        .withBody(Json.toJson(response).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori.futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/"))
        .willReturn(aResponse().withStatus(NOT_FOUND)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori.futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual NOT_FOUND }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/"))
        .willReturn(aResponse().withStatus(BAD_REQUEST)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori.futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual BAD_REQUEST }
    }

    "must return HttpResponse with an internal server if there is an internal server error" in {
      val connector = app.injector.instanceOf[DeparturesConnector]
      server.stubFor(get(urlEqualTo("/transits-movements-trader-at-departure/movements/departures/"))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getForEori.futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }

  }

  override protected def portConfigKey: String = "microservice.services.transits-movements-trader-at-departure.port"
}
