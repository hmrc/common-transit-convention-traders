package connectors

import java.time.LocalDateTime

import org.scalatest.{FreeSpec, MustMatchers}
import com.github.tomakehurst.wiremock.client.WireMock._
import models.domain.{Arrival, ArrivalWithMessages, MovementMessage}
import models.response.{ResponseArrival, ResponseArrivalWithMessages, ResponseMessage}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.Json
import play.api.mvc.Headers
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global

class ArrivalConnectorSpec extends FreeSpec with MustMatchers with WiremockSuite with ScalaFutures with IntegrationPatience with ScalaCheckPropertyChecks {
  "post" - {
    "must return ACCEPTED when post is successful" in {
      val connector = app.injector.instanceOf[ArrivalConnector]

      server.stubFor(
        post(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/")
        ).willReturn(aResponse().withStatus(ACCEPTED))
      )

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.post("<document></document>").futureValue

      result.status mustEqual ACCEPTED
    }

    "must return INTERNAL_SERVER_ERROR when post" - {
      "returns INTERNAL_SERVER_ERROR" in {
        val connector = app.injector.instanceOf[ArrivalConnector]

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/")
          ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
        )

        implicit val hc = HeaderCarrier()
        implicit val requestHeader = FakeRequest()

        val result = connector.post("<document></document>").futureValue

        result.status mustEqual INTERNAL_SERVER_ERROR
      }

    }

    "must return BAD_REQUEST when post returns BAD_REQUEST" in {
      val connector = app.injector.instanceOf[ArrivalConnector]

      server.stubFor(
        post(
          urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/")
        ).willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.post("<document></document>").futureValue

      result.status mustEqual BAD_REQUEST
    }
  }

  "put" - {
    "must return ACCEPTED when put is successful" in {
        val connector = app.injector.instanceOf[ArrivalConnector]

        server.stubFor(
          put(
            urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/2")
          ).willReturn(aResponse().withStatus(ACCEPTED))
        )

        implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

        val result = connector.put("<document></document>", "2").futureValue

        result.status mustEqual ACCEPTED
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

        val result = connector.put("<document></document>", "2").futureValue

        result.status mustEqual INTERNAL_SERVER_ERROR
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

      val result = connector.put("<document></document>", "2").futureValue

      result.status mustEqual BAD_REQUEST
    }

  }

  "get" - {
    "must return Arrival when arrival is found" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      val arrival = Arrival(1, "/movements/arrivals/1", "/movements/arrivals/1/messages", "MRN", "status", LocalDateTime.now, LocalDateTime.now)

      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1"))
        .willReturn(aResponse().withStatus(OK)
        .withBody(Json.toJson(arrival).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result mustEqual Right(arrival)
    }

    "must return HttpResponse with an internal server error if there is a model mismatch" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      val arrival = Arrival(1, "/movements/arrivals/1", "/movements/arrivals/1/messages", "MRN", "status", LocalDateTime.now, LocalDateTime.now)

      val response = ResponseArrival(arrival)

      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1"))
        .willReturn(aResponse().withStatus(OK)
          .withBody(Json.toJson(response).toString())))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }

    "must return HttpResponse with a not found if not found" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1"))
        .willReturn(aResponse().withStatus(NOT_FOUND)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual NOT_FOUND }
    }

    "must return HttpResponse with a bad request if there is a bad request" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1"))
        .willReturn(aResponse().withStatus(BAD_REQUEST)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual BAD_REQUEST }
    }

    "must return HttpResponse with an internal server if there is an internal server error" in {
      val connector = app.injector.instanceOf[ArrivalConnector]
      server.stubFor(get(urlEqualTo("/transit-movements-trader-at-destination/movements/arrivals/1"))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      implicit val hc = HeaderCarrier()
      implicit val requestHeader = FakeRequest()

      val result = connector.get("1").futureValue

      result.isLeft mustEqual true
      result.left.map { x => x.status mustEqual INTERNAL_SERVER_ERROR }
    }
  }

  override protected def portConfigKey: String = "microservice.services.transit-movement-trader-at-destination.port"
}
