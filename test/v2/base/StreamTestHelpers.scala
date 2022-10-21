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

package v2.base

import akka.stream.alpakka.xml.ParseEvent
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import java.nio.charset.StandardCharsets
import scala.xml.NodeSeq

object StreamTestHelpers extends StreamTestHelpers

trait StreamTestHelpers {

  def createStream(node: NodeSeq): Source[ByteString, _] = createStream(node.mkString)

  def createStream(json: JsObject): Source[ByteString, _] = createStream(Json.stringify(json))

  def createStream(string: String): Source[ByteString, _] =
    Source.single(ByteString(string, StandardCharsets.UTF_8))

  def createParsingEventStream(node: NodeSeq): Source[ParseEvent, _] =
    createStream(node).via(XmlParsing.parser)

}
