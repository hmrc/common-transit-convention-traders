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
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import config.AppConfig
import io.lemonlabs.uri.Url
import org.mockito.Mockito.when
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.HttpClientV2Support
import utils.TestMetrics
import utils.WiremockSuite
import v2.base.TestActorSystem
import v2.models.responses.UpscanFormTemplate
import v2.models.responses.UpscanInitiateResponse
import v2.models.responses.UpscanReference
import v2.models.responses.UpscanResponse.DownloadUrl
import v2.utils.CommonGenerators

import scala.concurrent.ExecutionContext.Implicits.global

class UpscanConnectorSpec
    extends AnyFreeSpec
    with HttpClientV2Support
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WiremockSuite
    with MockitoSugar
    with CommonGenerators
    with TestActorSystem {

  val mockAppConfig = mock[AppConfig]

  val movementId   = arbitraryMovementId.arbitrary.sample.get
  val messageId    = arbitraryMessageId.arbitrary.sample.get
  val eoriNumber   = arbitraryEORINumber.arbitrary.sample.get
  val movementType = arbitraryMovementType.arbitrary.sample.get

  when(mockAppConfig.upscanInitiateUrl).thenAnswer {
    _ => Url.parse(server.baseUrl())
  }

  when(mockAppConfig.commmonTransitConventionTradersUrl).thenAnswer {
    _ => Url.parse(server.baseUrl())
  }

  lazy val sut = new UpscanConnectorImpl(mockAppConfig, httpClientV2, new TestMetrics)

  "POST /upscan/v2/initiate" - {
    "when making a successful call to upscan initiate, must return upscan upload url" in {
      server.stubFor(
        post(
          urlEqualTo("/upscan/v2/initiate")
        ).willReturn(
          aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(upscanResponse)))
        )
      )
      implicit val hc = HeaderCarrier()
      val result      = sut.upscanInitiate(eoriNumber, movementType, movementId, messageId)

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
      implicit val hc = HeaderCarrier()
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

  "GET /upscan/v2/file/{downloadUrl}" - {
    "when making a successful call to upscan get file, must return file content" in {
      val expectedResponse = "file content"
      server.stubFor(
        post(
//          urlEqualTo("/upscan/v2/file/download.url") // encode only path part of URL
          urlEqualTo("/upscan/v2/file/download.url")
        ).willReturn(
          aResponse().withStatus(OK).withBody(expectedResponse)
        )
      )
      implicit val hc = HeaderCarrier()
      val result      = sut.upscanGetFile(DownloadUrl("http://download.url"))

      whenReady(result) {
        _ mustBe expectedResponse
      }

    }

    "when making a failure call to upscan get file, an exception is returned in the future" in {
      server.stubFor(
        post(
          urlEqualTo("/upscan/v2/file/download.url") // encode only path part of URL
        ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )
      implicit val hc = HeaderCarrier()
      val result = sut
        .upscanGetFile(DownloadUrl("http://download.url"))
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

  //  "GET /upscan/v2/file/{downloadUrl}" - {
//    "when making a successful call to upscan get file, must return file content" in {
//      val expectedResponse = "file content"
//      server.stubFor(
//        post(
//          urlEqualTo("/upscan/v2/file/http%3A%2F%2Fdownload.url") // encode URL
//        ).willReturn(
//          aResponse().withStatus(OK).withBody(expectedResponse)
//        )
//      )
//      implicit val hc = HeaderCarrier()
//      val result      = sut.upscanGetFile(DownloadUrl("http://download.url"))
//
//      whenReady(result) {
//        _ mustBe expectedResponse
//      }
//
//    }
//
//    "when making a failure call to upscan get file, an exception is returned in the future" in {
//      server.stubFor(
//        post(
//          urlEqualTo("/upscan/v2/file/http%3A%2F%2Fdownload.url") // encode URL
//        ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
//      )
//      implicit val hc = HeaderCarrier()
//      val result = sut
//        .upscanGetFile(DownloadUrl("http://download.url"))
//        .map(
//          _ => fail("A success was recorded when it shouldn't have been")
//        )
//        .recover {
//          case _ => ()
//        }
//
//      whenReady(result) {
//        _ => // if we get here, we have a success and a Unit, so all is okay!
//      }
//    }
//  }

  //  "GET /upscan/v2/file/{downloadUrl}" - {
//    "when making a successful call to upscan get file, must return file content" in {
//      val expectedResponse = "file content"
//      server.stubFor(
//        post(
//          urlEqualTo("/upscan/v2/file/http://download.url")
//        ).willReturn(
//          aResponse().withStatus(OK).withBody(expectedResponse)
//        )
//      )
//      implicit val hc = HeaderCarrier()
//      val result      = sut.upscanGetFile(DownloadUrl("http://download.url"))
//
//      whenReady(result) {
//        _ mustBe expectedResponse
//      }
//
//    }
//
//    "when making a failure call to upscan get file, an exception is returned in the future" in {
//      server.stubFor(
//        post(
//          urlEqualTo("/upscan/v2/file/http://download.url")
//        ).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
//      )
//      implicit val hc = HeaderCarrier()
//      val result = sut
//        .upscanGetFile(DownloadUrl("http://download.url"))
//        .map(
//          _ => fail("A success was recorded when it shouldn't have been")
//        )
//        .recover {
//          case _ => ()
//        }
//
//      whenReady(result) {
//        _ => // if we get here, we have a success and a Unit, so all is okay!
//      }
//    }
//  }

  private def upscanResponse =
    UpscanInitiateResponse(
      UpscanReference("b72d9aea-fdb9-40f1-800c-3612154baf07"),
      UpscanFormTemplate(
        "http://localhost:9570/upscan/upload-proxy",
        Map(
          "x-amz-meta-callback-url"             -> "https://myservice.com/callback",
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
