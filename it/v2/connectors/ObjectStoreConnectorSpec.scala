/*
 * Copyright 2023 HM Revenue & Customs
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
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Application
import play.api.inject
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.mvc.Http.Status
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.RetentionPeriod.SevenYears
import uk.gov.hmrc.objectstore.client.config.ObjectStoreClientConfig
import uk.gov.hmrc.objectstore.client.play.test.stub
import uk.gov.hmrc.objectstore.client.play.test.stub.StubPlayObjectStoreClientEither
import v2.base.TestActorSystem
import v2.models.responses.UpscanResponse.DownloadUrl
import v2.utils.CommonGenerators

import java.time.Clock
import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext

class ObjectStoreConnectorSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with Matchers
    with TestActorSystem
    with ScalaFutures
    with IntegrationPatience
    with ScalaCheckDrivenPropertyChecks
    with CommonGenerators {

  val baseUrl = s"baseUrl-${randomUUID().toString}"
  val owner   = s"owner-${randomUUID().toString}"
  val token   = s"token-${randomUUID().toString}"
  val config  = ObjectStoreClientConfig(baseUrl, owner, token, SevenYears)

  val mockClock            = mock[Clock]
  lazy val objectStoreStub = new stub.StubPlayObjectStoreClientEither(config)

  override lazy val app: Application =
    GuiceApplicationBuilder()
      .overrides(inject.bind(classOf[StubPlayObjectStoreClientEither]).toInstance(objectStoreStub), inject.bind(classOf[Clock]).toInstance(mockClock))
      .build()

  val movementId = arbitraryMovementId.arbitrary.sample.get
  val messageId  = arbitraryMessageId.arbitrary.sample.get

  implicit val hc                        = HeaderCarrier()
  implicit lazy val ec: ExecutionContext = materializer.executionContext

  val objectSummary = arbitraryObjectSummaryWithMd5.arbitrary.sample.get

  "when making a successful call to object store, must return object summary" in {
    val response = Json.obj(
      "location"      -> objectSummary.location.asUri,
      "contentLength" -> objectSummary.contentLength,
      "contentMd5"    -> objectSummary.contentMd5.value,
      "lastModified"  -> objectSummary.lastModified.toString
    )

    stubFor(
      put(urlEqualTo(s"/object-store/movements/${movementId.value}/messages/${messageId.value}.xml"))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withHeader(HeaderNames.CONTENT_TYPE, ContentTypes.JSON)
            .withBody(Json.stringify(response))
        )
    )

    val connector = app.injector.instanceOf[ObjectStoreConnector]

    whenReady(connector.postFromUrl(DownloadUrl("upscanUrl"), movementId, messageId)) {
      result =>
        result mustBe Right(objectSummary)
    }

  }
}
