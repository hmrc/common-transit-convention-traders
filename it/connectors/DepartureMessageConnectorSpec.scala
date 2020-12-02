package connectors

import java.time.LocalDateTime

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo}
import controllers.routes
import models.domain.{Departure, DepartureWithMessages, MovementMessage}
import models.response.ResponseDeparture
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import uk.gov.hmrc.http.HeaderCarrier
import utils.CallOps._

import scala.concurrent.ExecutionContext.Implicits.global

class DepartureMessageConnectorSpec extends AnyFreeSpec with Matchers with WiremockSuite with ScalaFutures with IntegrationPatience with ScalaCheckPropertyChecks {

  "get" - {
    "must return MovementMessage when message is found" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      val movement = MovementMessage(routes.DepartureMessagesController.getDepartureMessage("1","1").urlWithContext, LocalDateTime.now, "abc", <test>default</test>)
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages/1")
        ).willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(movement).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1", "1").futureValue

      result mustEqual Right(movement)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      val departure = Departure(1, routes.DeparturesController.getDeparture("1").urlWithContext, routes.DepartureMessagesController.getDepartureMessages("1").urlWithContext, Some("MRN"), "status", LocalDateTime.now, LocalDateTime.now)

      val response = ResponseDeparture(departure)
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages/1")
        ).willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(response).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1", "1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages/1")
        ).willReturn(aResponse().withStatus(NOT_FOUND)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1", "1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual NOT_FOUND }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages/1")
        ).willReturn(aResponse().withStatus(BAD_REQUEST)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1", "1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual BAD_REQUEST }
    }

    "must return HttpResponse with an internal server if if there is an internal server error" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages/1")
        ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1", "1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }
  }

  "getDepartureMessages" - {
    "must return Departure when departure is found" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      val departure = DepartureWithMessages(1, routes.DeparturesController.getDeparture("1").urlWithContext, routes.DepartureMessagesController.getDepartureMessages("1").urlWithContext, Some("MRN"), "status", LocalDateTime.now, LocalDateTime.now,
        Seq(
          MovementMessage(routes.DepartureMessagesController.getDepartureMessage("1","1").urlWithContext, LocalDateTime.now, "abc", <test>default</test>),
          MovementMessage(routes.DepartureMessagesController.getDepartureMessage("1","2").urlWithContext, LocalDateTime.now, "abc", <test>default</test>)
        ))

      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages")
        ).willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(departure).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getMessages("1").futureValue

      result mustEqual Right(departure)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      val departure = Departure(1, routes.DeparturesController.getDeparture("1").urlWithContext, routes.DepartureMessagesController.getDepartureMessages("1").urlWithContext, Some("MRN"), "status", LocalDateTime.now, LocalDateTime.now)

      val response = ResponseDeparture(departure)
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages")
        ).willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(response).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getMessages("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages")
        ).willReturn(aResponse().withStatus(NOT_FOUND)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getMessages("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual NOT_FOUND }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages")
        ).willReturn(aResponse().withStatus(BAD_REQUEST)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getMessages("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual BAD_REQUEST }
    }

    "must return HttpResponse with an internal server if there is an internal server error" in {
      val connector = app.injector.instanceOf[DepartureMessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transits-movements-trader-at-departure/movements/departures/1/messages")
        ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.getMessages("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }
  }

  //TODO: Refactor this and other spec usages to a common trait
  override protected def portConfigKey: String = "microservice.services.transits-movements-trader-at-departure.port"
}