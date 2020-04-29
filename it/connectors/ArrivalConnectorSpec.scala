package connectors

import org.scalatest.{FreeSpec, MustMatchers}
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

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

      val result = connector.put("<document></document>", "2").futureValue

      result.status mustEqual BAD_REQUEST
    }

  }

  override protected def portConfigKey: String = "microservice.services.transit-movement-trader-at-destination.port"
}
