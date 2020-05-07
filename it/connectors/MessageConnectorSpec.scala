package connectors

import java.time.LocalDateTime

import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import com.github.tomakehurst.wiremock.client.WireMock._
import models.domain.{Arrival, MovementMessage}
import models.response.{ResponseArrival, ResponseMessage}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global

class MessageConnectorSpec extends FreeSpec with MustMatchers with WiremockSuite with ScalaFutures with IntegrationPatience with ScalaCheckPropertyChecks {

  "get" - {
    "must return MovementMessage when message is found" in {
      val connector = app.injector.instanceOf[MessageConnector]
      val movement = MovementMessage("/movements/arrivals/1/messages/1", LocalDateTime.now, "abc", <test>default</test>)
      server.stubFor(
        get(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1/messages/1")
        ).willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(movement).toString())))

      implicit val hc = HeaderCarrier()

      val result = connector.get("1", "1").futureValue

      result mustEqual Right(movement)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[MessageConnector]
      val movement = MovementMessage("/movements/arrivals/1/messages/1", LocalDateTime.now, "abc", <test>default</test>)
      val response = ResponseMessage(movement)
      server.stubFor(
        get(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1/messages/1")
        ).willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(response).toString())))

      implicit val hc = HeaderCarrier()

      val result = connector.get("1", "1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[MessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1/messages/1")
        ).willReturn(aResponse().withStatus(NOT_FOUND)))

      implicit val hc = HeaderCarrier()

      val result = connector.get("1", "1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual NOT_FOUND }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[MessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1/messages/1")
        ).willReturn(aResponse().withStatus(BAD_REQUEST)))

      implicit val hc = HeaderCarrier()

      val result = connector.get("1", "1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual BAD_REQUEST }
    }

    "must return HttpResponse with an internal server if if there is an internal server error" in {
      val connector = app.injector.instanceOf[MessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1/messages/1")
        ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      implicit val hc = HeaderCarrier()

      val result = connector.get("1", "1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }
  }

  "getArrivalMessages" - {
    "must return Arrival when arrival is found" in {
      val connector = app.injector.instanceOf[MessageConnector]
      val arrival = Arrival(1, "/movements/arrivals/1", "/movements/arrivals/1/messages", "MRN", "status", LocalDateTime.now, LocalDateTime.now,
        Seq(
          MovementMessage("/movements/arrivals/1/messages/1", LocalDateTime.now, "abc", <test>default</test>),
          MovementMessage("/movements/arrivals/1/messages/2", LocalDateTime.now, "abc", <test>default</test>)
        ))

      server.stubFor(
        get(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1/messages")
        ).willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(arrival).toString())))

      implicit val hc = HeaderCarrier()

      val result = connector.getArrivalMessages("1").futureValue

      result mustEqual Right(arrival)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[MessageConnector]
      val arrival = Arrival(1, "/movements/arrivals/1", "/movements/arrivals/1/messages", "MRN", "status", LocalDateTime.now, LocalDateTime.now,
        Seq(
          MovementMessage("/movements/arrivals/1/messages/1", LocalDateTime.now, "abc", <test>default</test>),
          MovementMessage("/movements/arrivals/1/messages/2", LocalDateTime.now, "abc", <test>default</test>)
        ))

      val response = ResponseArrival(arrival)
      server.stubFor(
        get(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1/messages")
        ).willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(response).toString())))

      implicit val hc = HeaderCarrier()

      val result = connector.getArrivalMessages("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[MessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1/messages")
        ).willReturn(aResponse().withStatus(NOT_FOUND)))

      implicit val hc = HeaderCarrier()

      val result = connector.getArrivalMessages("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual NOT_FOUND }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[MessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1/messages")
        ).willReturn(aResponse().withStatus(BAD_REQUEST)))

      implicit val hc = HeaderCarrier()

      val result = connector.getArrivalMessages("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual BAD_REQUEST }
    }

    "must return HttpResponse with an internal server if if there is an internal server error" in {
      val connector = app.injector.instanceOf[MessageConnector]
      server.stubFor(
        get(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1/messages")
        ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      implicit val hc = HeaderCarrier()

      val result = connector.getArrivalMessages("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }
  }

  override protected def portConfigKey: String = "microservice.services.transit-movement-trader-at-destination.port"
}
