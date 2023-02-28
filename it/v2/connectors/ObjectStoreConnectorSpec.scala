package v2.connectors

import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import config.AppConfig
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.test.HttpClientV2Support
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClientEither
import utils.WiremockSuite
import v2.base.CommonGenerators

import java.time.Clock

class ObjectStoreConnectorSpec
    extends AnyFreeSpec
    with HttpClientV2Support
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WiremockSuite
    with MockitoSugar
    with CommonGenerators {

  val mockPlayObjectStoreClientEither = mock[PlayObjectStoreClientEither]
  val mockClock                       = mock[Clock]

  val movementId = arbitraryMovementId.arbitrary.sample.get
  val messageId  = arbitraryMessageId.arbitrary.sample.get

  lazy val sut = new ObjectStoreConnectorImpl(mockPlayObjectStoreClientEither, mockClock)

  "POST /upscan/v2/initiate" - {
    "when making a successful call to upscan initiate, must return upscan upload url" in {
      //      server.stubFor(
      //        post(
      //          urlEqualTo("/upscan/v2/initiate")
      //        ).willReturn(
      //          aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(upscanResponse)))
      //        )
      //      )
      //      implicit val hc = HeaderCarrier()
      //      val result = sut.upscanInitiate(movementId, messageId)
      //
      //      whenReady(result) {
      //        _ mustBe upscanResponse
      //      }

    }

  }
}
