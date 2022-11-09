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

import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsNumber
import play.api.libs.json.JsNumber
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import v2.base.TestActorSystem
import v2.base.TestSourceProvider

class StreamingUtilsSpec extends AnyFreeSpec with Matchers with MockitoSugar with ScalaFutures with TestActorSystem with TestSourceProvider {

  "StreamingUtils" - {

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

    "mergeStreamIntoJson" - {

      "when providing a stream should produce expected Json" in {

        val json = Json.obj(
          "a" -> "b",
          "b" -> 1,
          "c" -> Json.obj(
            "d" -> 1,
            "e" -> "f"
          )
        )

        val stream: Source[ByteString, _] = Source.single(ByteString("abc"))

        val resultStream = StreamingUtils.mergeStreamIntoJson(json.fields, "body", stream)
        val res = resultStream
          .reduce(_ ++ _)
          .map(
            str => Json.parse(str.utf8String)
          )
          .runWith(Sink.head)
        whenReady(res) {
          _ mustBe Json.obj(
            "a" -> JsString("b"),
            "b" -> JsNumber(1),
            "c" -> Json.obj(
              "d" -> 1,
              "e" -> "f"
            ),
            "body" -> "abc"
          )
        }
      }

      "when providing a stream that contains XML should produce expected Json wrapping the XML with proper escaping" in {

        val fields = Seq[(String, JsValue)](
          "a" -> JsString("b"),
          "b" -> JsNumber(1),
          "c" -> Json.obj(
            "d" -> 1,
            "e" -> "f"
          )
        )

        val sampleXML = """<ncts:CC004C PhaseID="NCTS5.0" xmlns:ncts="http://ncts.dgtaxud.ec"><messageRecipient>3YDhC2ur8ES</messageRecipient></ncts:CC004C>"""

        val stream: Source[ByteString, _] = Source.single(ByteString(sampleXML))

        val resultStream = StreamingUtils.mergeStreamIntoJson(fields, "body", stream)
        val res = resultStream
          .reduce(_ ++ _)
          .map(
            str => Json.parse(str.utf8String)
          )
          .runWith(Sink.head)
        whenReady(res) {
          _ mustBe Json.obj(
            "a" -> JsString("b"),
            "b" -> JsNumber(1),
            "c" -> Json.obj(
              "d" -> 1,
              "e" -> "f"
            ),
            "body" -> sampleXML
          )
        }
      }

    }
  }

}
