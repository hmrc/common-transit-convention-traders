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

package v2_1.connectors

import com.codahale.metrics.MetricRegistry
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.github.tomakehurst.wiremock.client.WireMock._
import config.AppConfig
import org.apache.pekko.stream.Materializer
import org.scalatest.concurrent._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.OK
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.HttpClientV2Support
import utils._
import v2_1.models.HeaderTypes.jsonToXml
import v2_1.models.request.MessageType

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContextExecutor

class ConversionConnectorSpec
    extends AnyFreeSpec
    with HttpClientV2Support
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneAppPerSuite
    with GuiceWiremockSuite {

  lazy val appConfig: AppConfig                  = app.injector.instanceOf[AppConfig]
  implicit lazy val materializer: Materializer   = app.materializer
  implicit lazy val ec: ExecutionContextExecutor = materializer.executionContext
  implicit lazy val hc: HeaderCarrier            = HeaderCarrier()
  lazy val messageType                           = MessageType.DeclarationData
  lazy val jsonStream                            = Source.single(ByteString("{}", StandardCharsets.UTF_8))

  lazy val sut: ConversionConnectorImpl = new ConversionConnectorImpl(httpClientV2, appConfig, new MetricRegistry)

  "POST /messages/:messageType " - {
    "when making a successful submission, must return successful" in {
      server.stubFor(
        post(
          urlEqualTo(s"/transit-movements-converter/messages/${messageType.code}")
        )
          .willReturn(
            aResponse().withStatus(OK).withBody("a response from the converter")
          )
      )

      whenReady(sut.post(MessageType.DeclarationData, jsonStream, jsonToXml)) {
        _.reduce(_ ++ _)
          .map(_.utf8String)
          .runWith(Sink.last)
          .map {
            _ mustBe "a response from the converter"
          }
      }
    }

    "when making an unsuccessful submission must return an error response" in {
      server.stubFor(
        post(
          urlEqualTo(s"/transit-movements-converter/messages/${messageType.code}")
        )
          .willReturn(
            aResponse().withStatus(500).withBody("Internal service error")
          )
      )

      val postResponse = sut.post(MessageType.DeclarationData, jsonStream, jsonToXml)
      val failedResponse = postResponse
        .map(
          _ => fail("Future unexpectedly succeeded, expected and UpstreamErrorResponse")
        )
        .recover {
          case error @ UpstreamErrorResponse("Internal service error", 500, _, _) => error
          case _                                                                  => fail("Future failed but with unexpected arguments")
        }
      whenReady(failedResponse) {
        _ mustBe UpstreamErrorResponse("Internal service error", 500)
      }
    }
  }

  override protected def portConfigKey: Seq[String] = Seq("microservice.services.transit-movements-converter.port")
}
