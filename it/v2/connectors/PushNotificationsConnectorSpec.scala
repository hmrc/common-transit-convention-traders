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

package v2.connectors

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import config.AppConfig
import io.lemonlabs.uri.Url
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.CREATED
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.HttpClientV2Support
import utils.TestMetrics
import utils.WiremockSuite
import v2.models.MovementId
import v2.models.request.PushNotificationsAssociation
import v2.utils.CommonGenerators

import scala.concurrent.ExecutionContext.Implicits.global

class PushNotificationsConnectorSpec
    extends AnyFreeSpec
    with HttpClientV2Support
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WiremockSuite
    with MockitoSugar
    with CommonGenerators {

  val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.pushNotificationsUrl).thenAnswer {
    _ => Url.parse(server.baseUrl())
  }
  // using thenAnswer for lazy semantics

  lazy val sut = new PushNotificationsConnectorImpl(mockAppConfig, httpClientV2, new TestMetrics)

  def targetUrl(movementId: MovementId) = s"/transit-movements-push-notifications/traders/movements/${movementId.value}/box"

  "when sending a result that returns Created, unit is returned" in forAll(arbitrary[MovementId], arbitrary[PushNotificationsAssociation]) {
    (movementId, assoc) =>
      val mapping =
        post(
          urlEqualTo(targetUrl(movementId))
        )
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
          .withRequestBody(matchingJsonPath(s"$$[?(@.clientId == '${assoc.clientId.value}')]"))
          .withRequestBody(matchingJsonPath(s"$$[?(@.movementType == '${assoc.movementType.movementType}')]"))
          .willReturn(aResponse().withStatus(CREATED))

      // given this endpoint
      server.stubFor(
        // ifwe have a box, make sure that is in the Json too.
        assoc.boxId
          .map(
            box => mapping.withRequestBody(matchingJsonPath(s"$$[?(@.boxId == '${box.value}')]"))
          )
          .getOrElse(mapping)
      )

      implicit val hc = HeaderCarrier()

      val result = sut.postAssociation(movementId, assoc)
      whenReady(result) {
        _ => // if we get here, we have a success and a Unit, so all is okay!
      }
  }

  "when sending a result that is a server error, an exception is returned in the future" in forAll(
    arbitrary[MovementId],
    arbitrary[PushNotificationsAssociation]
  ) {
    (movementId, assoc) =>
      // given this endpoint
      server.stubFor(
        post(
          urlEqualTo(targetUrl(movementId))
        )
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      implicit val hc = HeaderCarrier()

      val result = sut
        .postAssociation(movementId, assoc)
        .map(
          _ => fail("A success was recorded when it shouldn't have been")
        )
        .recover {
          case UpstreamErrorResponse(_, INTERNAL_SERVER_ERROR, _, _) => ()
        }

      whenReady(result) {
        _ => // if we get here, we have a success and a Unit, so all is okay!
      }
  }

}
