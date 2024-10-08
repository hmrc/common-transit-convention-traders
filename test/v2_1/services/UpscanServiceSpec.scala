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

package v2_1.services

import models.common.EORINumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import models.common.errors.UpscanError
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.http.HeaderCarrier
import v2_1.base.TestActorSystem
import v2_1.base.TestCommonGenerators
import v2_1.connectors.UpscanConnector
import v2_1.models.responses.UpscanFormTemplate
import v2_1.models.responses.UpscanInitiateResponse
import v2_1.models.responses.UpscanReference
import v2_1.models.responses.UpscanResponse.DownloadUrl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class UpscanServiceSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with TestCommonGenerators
    with ScalaCheckDrivenPropertyChecks
    with BeforeAndAfterEach
    with TestActorSystem {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConnector: UpscanConnector = mock[UpscanConnector]

  val sut = new UpscanServiceImpl(mockConnector)

  override protected def beforeEach(): Unit =
    reset(mockConnector)

  "upscan initiate" - {

    "when a call to upscanInitiate is success, assert that response contains uploadRequest details" in forAll(
      arbitraryEORINumber.arbitrary,
      arbitraryMovementType.arbitrary,
      arbitraryMovementId.arbitrary,
      arbitraryMessageId.arbitrary
    ) {
      (eoriNumber, movementType, movementId, messageId) =>
        beforeEach()

        when(
          mockConnector
            .upscanInitiate(
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementType],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId]
            )(any(), any())
        )
          .thenReturn(Future.successful(upscanResponse))

        val result = sut.upscanInitiate(eoriNumber, movementType, movementId, messageId)

        whenReady(result.value) {
          r => r mustBe Right(upscanResponse)
        }

    }

    "when an error occurs, return a Left of Unexpected" in forAll(
      arbitraryEORINumber.arbitrary,
      arbitraryMovementType.arbitrary,
      arbitraryMovementId.arbitrary,
      arbitraryMessageId.arbitrary
    ) {
      (eoriNumber, movementType, movementId, messageId) =>
        beforeEach()

        val expectedException = new Exception()
        when(
          mockConnector
            .upscanInitiate(
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementType],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId]
            )(any(), any())
        )
          .thenReturn(Future.failed(expectedException))

        val result = sut.upscanInitiate(eoriNumber, movementType, movementId, messageId)

        whenReady(result.value) {
          r => r mustBe Left(UpscanError.UnexpectedError(thr = Some(expectedException)))
        }

    }
  }

  "upscanGetFile" - {
    val downloadUrl = DownloadUrl("http://download.url")
    implicit val patienceConfig: PatienceConfig =
      PatienceConfig(Span(5, Seconds), Span(100, Millis))

    "should return Right(result) if the connector returns the result successfully" in {
      val expected = "file content"
      when(mockConnector.upscanGetFile(downloadUrl)).thenReturn(Future.successful(Source.single(ByteString(expected))))

      val result = sut.upscanGetFile(downloadUrl).value

      whenReady(result) {
        r =>
          val expectedSource = Source.single(ByteString(expected))
          val actualSource   = r.getOrElse(Source.empty)
          val expectedBytes  = Await.result(expectedSource.runWith(Sink.seq), Duration.Inf).flatten
          val actualBytes    = Await.result(actualSource.runWith(Sink.seq), Duration.Inf).flatten
          actualBytes mustBe expectedBytes
      }
    }

    "should return Left(UpscanInitiateError.UnexpectedError(Some(throwable))) if the connector fails with a NonFatal error" in {
      val expectedError = new RuntimeException("something went wrong")
      when(mockConnector.upscanGetFile(downloadUrl)).thenReturn(Future.failed(expectedError))

      val result = sut.upscanGetFile(downloadUrl).value

      whenReady(result) {
        r =>
          r mustBe Left(UpscanError.UnexpectedError(Some(expectedError)))
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
