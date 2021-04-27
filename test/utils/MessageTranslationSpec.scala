/*
 * Copyright 2021 HM Revenue & Customs
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

package utils

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.running

class MessageTranslationSpec extends AnyFreeSpec with Matchers {

  val appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "message-translation-file" -> "TestMessageTranslation.json",
        "metrics.jvm" -> false
      )

  ".translate" - {

    "must replace any JSON keys that exist the message translation file with their translation" in {

      val app = appBuilder.build()

      running(app) {
        val service = app.injector.instanceOf[MessageTranslation]
        val inputJson = Json.obj(
          "field1" -> 1,
          "foo" -> Json.arr(
            Json.obj("field2" -> 2),
            Json.obj("field2" -> 3)
          )
        )

        val expectedResultJson = Json.obj(
          "Description 1" -> 1,
          "foo" -> Json.arr(
            Json.obj("Description 2" -> 2),
            Json.obj("Description 2" -> 3)
          )
        )

        val result = service.translate(inputJson)

        result mustEqual expectedResultJson
      }
    }

    "must not change any keys that aren't in the message translation file" in {

      val app = appBuilder.build()

      running(app) {
        val service = app.injector.instanceOf[MessageTranslation]
        val inputJson = Json.obj(
          "foo" -> 1,
          "bar" -> Json.arr(
            Json.obj("baz" -> 2),
            Json.obj("baz" -> 3)
          )
        )

        val result = service.translate(inputJson)

        result mustEqual inputJson
      }
    }

    "must not change any JSON values" in {

      val app = appBuilder.build()

      running(app) {
        val service = app.injector.instanceOf[MessageTranslation]
        val inputJson = Json.obj(
          "foo" -> "field1",
          "bar" -> Json.arr(
            Json.obj("baz" -> "field2"),
            Json.obj("baz" -> "field2")
          )
        )

        val result = service.translate(inputJson)

        result mustEqual inputJson
      }
    }
  }
}
