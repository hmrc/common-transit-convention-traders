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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.Md5Hash
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.RetentionPeriod
import uk.gov.hmrc.objectstore.client.RetentionPeriod.SevenYears
import uk.gov.hmrc.objectstore.client.config.ObjectStoreClientConfig
import uk.gov.hmrc.objectstore.client.play.FutureEither
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClientEither
import uk.gov.hmrc.objectstore.client.play.test.stub
import v2.base.TestCommonGenerators
import v2.base.TestActorSystem
import v2.fakes.objectstore.ObjectStoreStub
import v2.models.responses.UpscanResponse.DownloadUrl

import java.net.URL
import java.time.Clock
import java.util.UUID.randomUUID
import scala.concurrent.Future
import scala.util.Try

class ObjectStoreServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with TestCommonGenerators
    with TestActorSystem
    with BeforeAndAfterEach {

  val baseUrl = s"http://baseUrl-${randomUUID().toString}"
  val owner   = s"owner-${randomUUID().toString}"
  val token   = s"token-${randomUUID().toString}"
  val config  = ObjectStoreClientConfig(baseUrl, owner, token, SevenYears)

  implicit val hc = HeaderCarrier()
  implicit val ec = materializer.executionContext

  lazy val objectStoreStub = new ObjectStoreStub(config)

  val objectStoreService = new ObjectStoreServiceImpl(Clock.systemUTC(), objectStoreStub)

  "On adding a message to object store" - {
    "given a successful response from the connector, should return a Right with Object Store Summary" in forAll(
      arbitraryMovementId.arbitrary,
      arbitraryMessageId.arbitrary
    ) {
      (movemementId, messageId) =>
        val result = objectStoreService.addMessage(DownloadUrl("https://bucketName.s3.eu-west-2.amazonaws.com"), movemementId, messageId)

        whenReady(result.value, timeout(Span(6, Seconds))) {
          case Left(e)  => fail(e.toString)
          case Right(x) => x
        }
    }

    "given an exception is thrown in the service, should return a Left with the exception in an ObjectStoreError" in forAll(
      arbitraryMovementId.arbitrary,
      arbitraryMessageId.arbitrary
    ) {
      (movemementId, messageId) =>
        val result = objectStoreService.addMessage(DownloadUrl("invalidURL"), movemementId, messageId)

        whenReady(result.value) {
          case Right(_) => fail("should have returned a Left")
          case Left(x)  => x
        }
    }
  }
}
