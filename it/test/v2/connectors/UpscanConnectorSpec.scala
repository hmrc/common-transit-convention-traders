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

import com.codahale.metrics.MetricRegistry
import org.apache.pekko.stream.scaladsl.Sink
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import config.AppConfig
import io.lemonlabs.uri.Url
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.HttpClientV2Support
import utils.WiremockSuite
import v2.base.TestActorSystem
import v2.models.ClientId
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.responses.UpscanFormTemplate
import v2.models.responses.UpscanInitiateResponse
import v2.models.responses.UpscanReference
import v2.models.responses.UpscanResponse.DownloadUrl
import v2.utils.CommonGenerators

import scala.concurrent.ExecutionContext.Implicits.global

class UpscanConnectorSpec
    extends AnyFreeSpec
    with BeforeAndAfterEach
    with HttpClientV2Support
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WiremockSuite
    with MockitoSugar
    with CommonGenerators
    with TestActorSystem
    with ScalaCheckDrivenPropertyChecks {

  val mockAppConfig: AppConfig = mock[AppConfig]

  val movementId: MovementId     = arbitraryMovementId.arbitrary.sample.get
  val messageId: MessageId       = arbitraryMessageId.arbitrary.sample.get
  val eoriNumber: EORINumber     = arbitraryEORINumber.arbitrary.sample.get
  val movementType: MovementType = arbitraryMovementType.arbitrary.sample.get

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAppConfig)

    when(mockAppConfig.upscanInitiateUrl).thenAnswer {
      _ => Url.parse(server.baseUrl())
    }

    when(mockAppConfig.commmonTransitConventionTradersUrl).thenAnswer {
      _ => Url.parse("https://ctc.hmrc.gov.uk/")
    }

    when(mockAppConfig.upscanMaximumFileSize).thenReturn(2000)
  }

  lazy val sut = new UpscanConnectorImpl(mockAppConfig, httpClientV2, new MetricRegistry)

  "POST /upscan/v2/initiate" - {
    "when making a successful call to upscan initiate with client ID query strings turned on, must return upscan upload url" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[ClientId]
    ) {
      (eoriNumber, movementType, movementId, messageId, clientId) =>
        when(mockAppConfig.forwardClientIdToUpscan).thenReturn(true)
        server.stubFor(
          post(
            urlEqualTo("/upscan/v2/initiate")
          )
            .withRequestBody(
              equalToJson(s"""{
                  |    "callbackUrl": "https://ctc.hmrc.gov.uk/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}?clientId=${clientId.value}",
                  |    "maximumFileSize": 2000
                  |}
                  |""".stripMargin)
            )
            .willReturn(
              aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(upscanResponse)))
            )
        )
        implicit val hc: HeaderCarrier = HeaderCarrier(otherHeaders = Seq("X-Client-Id" -> clientId.value))
        val result                     = sut.upscanInitiate(eoriNumber, movementType, movementId, messageId)

        whenReady(result) {
          _ mustBe upscanResponse
        }

    }

    "when making a successful call to upscan initiate with client ID query strings turned off, must return upscan upload url" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[ClientId]
    ) {
      (eoriNumber, movementType, movementId, messageId, clientId) =>
        when(mockAppConfig.forwardClientIdToUpscan).thenReturn(false)
        server.stubFor(
          post(
            urlEqualTo("/upscan/v2/initiate")
          )
            .withRequestBody(
              equalToJson(s"""{
                   |    "callbackUrl": "https://ctc.hmrc.gov.uk/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}",
                   |    "maximumFileSize": 2000
                   |}
                   |""".stripMargin)
            )
            .willReturn(
              aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(upscanResponse)))
            )
        )
        implicit val hc: HeaderCarrier = HeaderCarrier(otherHeaders = Seq("X-Client-Id" -> clientId.value))
        val result                     = sut.upscanInitiate(eoriNumber, movementType, movementId, messageId)

        whenReady(result) {
          _ mustBe upscanResponse
        }

    }

    "when making a failure call to upscan initiate, an exception is returned in the future" in {
      server.stubFor(
        post(
          urlEqualTo("/upscan/v2/initiate")
        ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val result = sut
        .upscanInitiate(eoriNumber, movementType, movementId, messageId)
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

  "GET upscan call to download url" - {
    "when making a successful call to upscan get file, must return file content" in {
      val expectedResponse = "file content"
      server.stubFor(
        get(
          urlEqualTo("/") // encode only path part of URL
        ).willReturn(
          aResponse().withStatus(OK).withBody(expectedResponse)
        )
      )
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val result                     = sut.upscanGetFile(DownloadUrl(Url.parse(server.baseUrl()).toString()))

      whenReady(result) {
        _.reduce(_ ++ _)
          .map(_.utf8String)
          .runWith(Sink.last)
          .map {
            _ mustBe expectedResponse
          }
      }

    }

    "when making a failure call to upscan get file, an exception is returned in the future" in {
      server.stubFor(
        get(
          urlEqualTo("/") // encode only path part of URL
        ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val result = sut
        .upscanGetFile(DownloadUrl(Url.parse(server.baseUrl()).toString()))
        .map(
          _ => fail("A success was recorded when it shouldn't have been")
        )
        .recover {
          case _ => ()
        }

      whenReady(result) {
        _ => // if we get here, we have a success and a Unit, so all is okay!
      }
    }
  }

  private val upscanResponse =
    UpscanInitiateResponse(
      UpscanReference("b72d9aea-fdb9-40f1-800c-3612154baf07"),
      UpscanFormTemplate(
        "http://localhost:9570/upscan/upload-proxy",
        Map(
          "x-amz-meta-callback-url"             -> s"https://myservice.com/callback",
          "x-amz-date"                          -> "20230118T135545Z",
          "success_action_redirect"             -> "https://myservice.com/nextPage?key=b72d9aea-fdb9-40f1-800c-3612154baf07",
          "x-amz-credential"                    -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
          "x-amz-meta-upscan-initiate-response" -> "2023-01-18T13:55:45.715Z",
          "x-amz-meta-upscan-initiate-received" -> "2023-01-18T13:55:45.715Z",
          "x-amz-meta-request-id"               -> "7075a21c-c8f0-402e-9c9c-1eea546c6fbf",
          "x-amz-meta-original-filename"        -> "${filename}",
          "x-amz-algorithm"                     -> "AWS4-HMAC-SHA256",
          "key"                                 -> "b72d9aea-fdb9-40f1-800c-3612154baf07",
          "acl"                                 -> "private",
          "x-amz-signature"                     -> "xxxx",
          "error_action_redirect"               -> "https://myservice.com/errorPage",
          "x-amz-meta-session-id"               -> "3506d041-ba59-41ee-bb2c-bf0363163be3",
          "x-amz-meta-consuming-service"        -> "PostmanRuntime/7.29.2",
          "policy"                              -> "eyJjb25kaXRpb25zIjpbWyJjb250ZW50LWxlbmd0aC1yYW5nZSIsMCwxMDI0XV19"
        )
      )
    )
}
