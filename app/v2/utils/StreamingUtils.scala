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

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import v2.controllers.ErrorTranslator
import v2.models.errors.StreamingError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

object StreamingUtils extends ErrorTranslator {

  def convertSourceToString(
    source: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): EitherT[Future, StreamingError, String] = EitherT {
    source
      .reduce(_ ++ _)
      .map(_.utf8String)
      .runWith(Sink.head[String])
      .map(Right[StreamingError, String])
      .recover {
        case NonFatal(ex) => Left[StreamingError, String](StreamingError.UnexpectedError(Some(ex)))
      }
  }

  val START_TOKEN: Source[ByteString, NotUsed]      = Source.single(ByteString("{"))
  val END_TOKEN_SOURCE: Source[ByteString, NotUsed] = Source.single(ByteString("\"}"))

  def mergeStreamIntoJson(fields: Seq[(String, JsValue)], fieldName: String, stream: Source[ByteString, _]): Source[ByteString, _] =
    // We need to start the document and add the final field, and prepare for the string data coming in
    START_TOKEN ++
      Source
        .fromIterator(
          () => fields.iterator
        )
        .map {
          // convert fields to bytestrings with their values, need to ensure we keep the field names quoted,
          // separate with a colon and end with a comma, as per the Json spec
          tuple =>
            ByteString(s""""${tuple._1}":${Json.stringify(tuple._2)},""")
        } ++
      // start adding the new field
      Source.single(ByteString(s""""$fieldName":"""".stripMargin)) ++
      // our XML stream that we escape
      stream.map(
        bs => ByteString(bs.utf8String.replace("\"", "\\\""))
      ) ++
      // and the stream that ends our Json document
      END_TOKEN_SOURCE

}
