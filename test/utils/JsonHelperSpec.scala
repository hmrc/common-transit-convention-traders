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
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.running

class JsonHelperSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  val appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "message-translation-file" -> "TestMessageTranslation.json",
        "metrics.jvm" -> false
      )

  "JsonHelper" - {

    "must convert xml to json" in {
      val xml = <xml><test1>one</test1><test1>two</test1></xml>

      val app = appBuilder.build()

      running(app) {
        val jsonHelper = app.injector.instanceOf[JsonHelper]

        val expectedResult: JsObject = Json.obj("xml" -> Json.obj("test1" -> Json.arr("one", "two")))
        val result: JsObject         = jsonHelper.convertXmlToJson(xml)
        result mustBe expectedResult
      }
    }

    "must replace field names when a match is found in the message translation file, and leave others untouched" in {
      val xml = <xml><field1>1</field1><field2>2</field2><field3>3</field3></xml>

      val app = appBuilder.build()

      running(app) {
        val jsonHelper = app.injector.instanceOf[JsonHelper]

        val expectedResult: JsObject = Json.obj("xml" -> Json.obj("Description 1" -> 1, "Description 2" -> 2, "field3" -> 3))
        val result: JsObject         = jsonHelper.convertXmlToJson(xml)
        result mustBe expectedResult
      }
    }
  }
}
