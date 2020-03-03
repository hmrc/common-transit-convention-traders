package connectors

import org.scalatest.{FreeSpec, MustMatchers}
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class MessageConnectorSpec extends FreeSpec with MustMatchers with WiremockSuite with ScalaFutures with IntegrationPatience {
  private val connector = app.injector.instanceOf[MessageConnector]

  "post" - {
    "must return NO_CONTENT when post is successful" in {
      server.stubFor(
        post(
          urlEqualTo("/common-transit-convention-trader-at-destination/message-notification")
        ).willReturn(aResponse().withStatus(NO_CONTENT))
      )

      implicit val hc = HeaderCarrier()

      val result = connector.post(<document></document>).futureValue

      result.status mustEqual NO_CONTENT
    }
  }

  override protected def portConfigKey: String = "microservice.services.transit-movement-trader-at-destination.port"
}
