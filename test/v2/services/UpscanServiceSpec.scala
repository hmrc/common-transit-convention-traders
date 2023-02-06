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

package v2.services

import config.AppConfig
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.http.HeaderCarrier
import v2.base.CommonGenerators
import v2.connectors.UpscanConnector
import v2.models.MovementId
import v2.models.errors.UpscanInitiateError
import v2.models.request.UpscanInitiate
import v2.models.responses.UpscanFormTemplate
import v2.models.responses.UpscanInitiateResponse
import v2.models.responses.UpscanReference

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UpscanServiceSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with CommonGenerators
    with ScalaCheckDrivenPropertyChecks
    with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConnector: UpscanConnector = mock[UpscanConnector]
  val mockAppConfig: AppConfig       = mock[AppConfig]

  val sut = new UpscanServiceImpl(mockConnector, mockAppConfig)

  override protected def beforeEach(): Unit = {
    reset(mockConnector)
    reset(mockAppConfig)
  }

  "upscan initiate" - {

    "when a call to upscanInitiate is success, assert that response contains uploadRequest details" in forAll(arbitraryMovementId.arbitrary) {
      movementId =>
        beforeEach()

        when(mockConnector.upscanInitiate(any[String].asInstanceOf[MovementId])(any(), any()))
          .thenReturn(Future.successful(upscanResponse))

        val result = sut.upscanInitiate(movementId)

        whenReady(result.value) {
          r => r mustBe Right(upscanResponse)
        }

    }

    "when an error occurs, return a Left of Unexpected" in forAll(arbitraryMovementId.arbitrary) {
      movementId =>
        beforeEach()

        val expectedException = new Exception()
        when(mockConnector.upscanInitiate(any[String].asInstanceOf[MovementId])(any(), any()))
          .thenReturn(Future.failed(expectedException))

        val result = sut.upscanInitiate(movementId)

        whenReady(result.value) {
          r => r mustBe Left(UpscanInitiateError.UnexpectedError(thr = Some(expectedException)))
        }

    }
  }

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