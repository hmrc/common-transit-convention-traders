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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import uk.gov.hmrc.http.HeaderCarrier
import v2.base.CommonGenerators
import v2.base.TestActorSystem
import v2.connectors.ObjectStoreConnector
import v2.models.MessageId
import v2.models.MovementId
import v2.models.responses.UpscanResponse.DownloadUrl

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ObjectStoreServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with CommonGenerators
    with TestActorSystem
    with BeforeAndAfterEach {

  val mockConnector = mock[ObjectStoreConnector]
  val sut           = new ObjectStoreServiceImpl(mockConnector)
  implicit val hc   = HeaderCarrier()
  implicit val ec   = materializer.executionContext

  override def beforeEach = {
    super.beforeEach()
    reset(mockConnector)
  }

  "On adding a message to object store" - {
    "given a successful response from the connector, should return a Right with Object Store Summary" in forAll(
      arbitraryMovementId.arbitrary,
      arbitraryMessageId.arbitrary,
      arbitraryObjectSummaryWithMd5.arbitrary
    ) {
      (movemementId, messageId, objectSummaryWithMd5) =>
        beforeEach()

        when(mockConnector.postFromUrl(DownloadUrl(any[String]), MovementId(any[String]), MessageId(any[String]))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Right(objectSummaryWithMd5)))

        val result = sut.addMessage(DownloadUrl("https://bucketName.s3.eu-west-2.amazonaws.com"), movemementId, messageId)
        whenReady(result.value) {
          either =>
            either match {
              case Left(_)  => fail("should have returned object store with Md5")
              case Right(x) => x
            }
            verify(mockConnector, times(1)).postFromUrl(DownloadUrl(any[String]), MovementId(any[String]), MessageId(any[String]))(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        }
    }

    "given a failure from the connector, should return a Left with an Exception" in forAll(
      arbitraryMovementId.arbitrary,
      arbitraryMessageId.arbitrary
    ) {
      (movemementId, messageId) =>
        beforeEach()

        when(mockConnector.postFromUrl(DownloadUrl(any[String]), MovementId(any[String]), MessageId(any[String]))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Left(new Exception("Error"))))

        val result = sut.addMessage(DownloadUrl("https://bucketName.s3.eu-west-2.amazonaws.com"), movemementId, messageId)
        whenReady(result.value) {
          either =>
            either match {
              case Right(_) => fail("should have returned a Left")
              case Left(x)  => x
            }
            verify(mockConnector, times(1)).postFromUrl(DownloadUrl(any[String]), MovementId(any[String]), MessageId(any[String]))(
              any[HeaderCarrier],
              any[ExecutionContext]
            )

        }
    }
  }
}
