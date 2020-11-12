/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.libs.json.{JsObject, Json}

class JsonHelperSpec extends AnyFreeSpec with Matchers {

  "JsonHelper" - {

    "must convert xml to json" in {
      val xml = "<xml><test1>one</test1><test1>two</test1></xml>"

      val expectedResult: JsObject = Json.obj("xml" -> Json.obj("test1" -> Json.arr("one", "two")))
      val result: JsObject         = JsonHelper.convertXmlToJson(xml)
      result.toString mustBe expectedResult.toString()
    }

    "must convert nested single name xml to json" in {
      val xml = "<xml><test1><test2>one</test2><test2>two</test2></test1></xml>"

      val expectedResult: JsObject =
        Json.obj("xml" -> Json.obj("test1" -> Json.obj("test2" -> Json.arr("one", "two"))))
      val result: JsObject         = JsonHelper.convertXmlToJson(xml)
      result.toString mustBe expectedResult.toString()
    }

    "must convert nested multi name xml to json" in {
      val xml = "<xml><test1><test2>one</test2><test3>two</test3></test1></xml>"

      val expectedResult: JsObject =
        Json.obj("xml" -> Json.obj("test1" -> Json.obj("test2" -> "one", "test3" -> "two")))
      val result: JsObject         = JsonHelper.convertXmlToJson(xml)
      result.toString mustBe expectedResult.toString()
    }

    "must convert complex xml to json" in {
      val xml = "<xml><a><b>one</b><b>1</b><c>two</c></a><d>3</d><a>10</a><a>test</a></xml>"

      val expectedResult: JsObject =
        Json.obj("xml" -> Json.obj(
          "a" -> Json.arr(Json.obj("b" -> Json.arr("one", 1), "c" -> "two"), 10, "test"),
          "d" -> 3
        ))
      val result: JsObject         = JsonHelper.convertXmlToJson(xml)
      result.toString mustBe expectedResult.toString()
    }

    "must return empty JsObject on failing to convert xml to json" in {
      val invalidXml = "<xml><test1>one</test1><test1></xml>"

      val result: JsObject = JsonHelper.convertXmlToJson(invalidXml)
      result mustBe Json.obj()
    }
  }
}
