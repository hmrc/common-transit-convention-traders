/*
 * Copyright 2022 HM Revenue & Customs
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

package v2.utils

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import v2.base.TestActorSystem
import v2.base.TestSourceProvider

class StreamingUtilsSpec extends AnyFreeSpec with Matchers with MockitoSugar with ScalaFutures with TestActorSystem with TestSourceProvider {

  "MessageFormat" - {

    "convertSourceToString" - {

      implicit val ec = materializer.executionContext

      "successfully converts source to string" in {
        val jsonString = Json.stringify(Json.obj("testKey" -> "testValue"))
        val jsonSource = singleUseStringSource(jsonString)

        whenReady(StreamingUtils.convertSourceToString(jsonSource).value) {
          result =>
            result mustBe Right(jsonString)
        }

      }
    }
  }

}
