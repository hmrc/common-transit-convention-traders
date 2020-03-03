package connectors

import org.scalatest.{FreeSpec, MustMatchers}
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalacheck.Gen
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class MessageConnectorSpec extends FreeSpec with MustMatchers with WiremockSuite with ScalaFutures with IntegrationPatience with ScalaCheckPropertyChecks {
  "post" - {
    "must return NO_CONTENT when post is successful" in {
      val connector = app.injector.instanceOf[MessageConnector]

      server.stubFor(
        post(
          urlEqualTo("/common-transit-convention-trader-at-destination/message-notification")
        ).willReturn(aResponse().withStatus(NO_CONTENT))
      )

      implicit val hc = HeaderCarrier()

      val result = connector.post(<document></document>).futureValue

      result.status mustEqual NO_CONTENT
    }

    "throw exception when post is unsuccessful" in {
      val connector = app.injector.instanceOf[MessageConnector]

      val errorCodes = Gen.choose(400, 599)

      forAll(errorCodes) {
        errorCode =>

          server.stubFor(
            post(
              urlEqualTo("/common-transit-convention-trader-at-destination/message-notification")
            ).willReturn(aResponse().withStatus(errorCode))
          )

          implicit val hc = HeaderCarrier()

          val result = connector.post(<document></document>)

          whenReady(result.failed) {
            response =>
              response mustBe an[Exception]
          }
      }
    }
  }

  override protected def portConfigKey: String = "microservice.services.transit-movement-trader-at-destination.port"
}
