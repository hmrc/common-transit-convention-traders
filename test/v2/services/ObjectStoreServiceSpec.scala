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

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.RetentionPeriod.SevenYears
import uk.gov.hmrc.objectstore.client.config.ObjectStoreClientConfig
import v2.base.TestCommonGenerators
import v2.base.TestActorSystem
import v2.fakes.objectstore.ObjectStoreStub
import v2.models.MessageId
import v2.models.MovementId
import v2.models.ObjectStoreResourceLocation
import v2.models.responses.UpscanResponse.DownloadUrl

import java.time.Clock
import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContextExecutor

class ObjectStoreServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with TestCommonGenerators
    with TestActorSystem
    with BeforeAndAfterEach {

  val baseUrl = s"http://baseUrl-${randomUUID().toString}"
  val owner   = "common-transit-convention-traders"
  val token   = s"token-${randomUUID().toString}"
  val config: ObjectStoreClientConfig = ObjectStoreClientConfig(baseUrl, owner, token, SevenYears)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContextExecutor = materializer.executionContext

  lazy val objectStoreStub = new ObjectStoreStub(config)

  val objectStoreService = new ObjectStoreServiceImpl(Clock.systemUTC(), objectStoreStub)

  "On getting a message from object store" - {
    "given a successful response from the connector, should return a Right with Object Store Summary" in {
      val movementId       = MovementId("308c4a68e2cdc08f")
      val messageId        = MessageId("123c4a68e2ele08f")
      val addMessageResult = objectStoreStub.seed(Path.File(s"movements/${movementId.value}/${movementId.value}-${messageId.value}.xml"))

      val result = objectStoreService.getMessage(ObjectStoreResourceLocation(s"/movements/${movementId.value}/${movementId.value}-${messageId.value}.xml"))

      whenReady(result.value, timeout(Span(6, Seconds))) {
        case Left(e)  => fail(e.toString)
        case Right(x) => succeed
      }
    }

    "given an exception is thrown due to an invalid url, should return a Left with the exception in an ObjectStoreError" in {

      val result = objectStoreService.getMessage(ObjectStoreResourceLocation("invalid filename"))

      whenReady(result.value) {
        case Right(_) => fail("should have returned a Left")
        case Left(x)  => x
      }
    }
  }

}
