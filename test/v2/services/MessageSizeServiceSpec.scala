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
import org.mockito.Mockito.when
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import v2.models.errors.PresentationError

class MessageSizeServiceSpec extends AnyFreeSpec with MockitoSugar with ScalaCheckDrivenPropertyChecks with ScalaFutures {

  private val config: AppConfig = mock[AppConfig]

  val limit = 500000

  "Small message limit " - {
    val service = new MessageSizeService(config)
    when(config.largeMessageSizeLimit).thenReturn(limit)

    "should return false when below the limit" in forAll(Gen.choose(1, limit)) {
      size =>
        whenReady(service.contentSizeIsLessThanSmallMessageLimit(size).value) {
          _ mustBe Right(())
        }
    }

    "should return true when below the limit" in {
      whenReady(service.contentSizeIsLessThanSmallMessageLimit(limit + 1).value) {
        _ mustBe Left(PresentationError.entityTooLargeError(s"Your message size must be less than $limit bytes"))
      }
    }

  }
}
