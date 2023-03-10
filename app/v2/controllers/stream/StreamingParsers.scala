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

package v2.controllers.stream

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.implicits.catsSyntaxMonadError
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.Action
import play.api.mvc.ActionBuilder
import play.api.mvc.BaseControllerHelpers
import play.api.mvc.BodyParser
import play.api.mvc.Result
import v2.controllers.request.BodyReplaceableRequest
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait StreamingParsers {
  self: BaseControllerHelpers =>

  implicit val materializer: Materializer

  /*
    This keeps Play's connection thread pool outside of our streaming, and uses a cached thread pool
    to spin things up as needed. Additional defence against performance issues picked up in CTCP-1545.
   */
  implicit val materializerExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  lazy val streamFromMemory: BodyParser[Source[ByteString, _]] = BodyParser {
    _ =>
      Accumulator.source[ByteString].map(Right.apply)
  }

  implicit class ActionBuilderStreamHelpers[R[A] <: BodyReplaceableRequest[R, A]](actionBuilder: ActionBuilder[R, _]) {

    /** Updates the [[Source]] in the [[BodyReplaceableRequest]] with a version that can be used
      *  multiple times via the use of a temporary file.
      *
      *   @param block The code to use the with the reusable source
      *   @return An [[Action]]
      */
    // Implementation note: Tried to use the temporary file parser but it didn't pass the "single use" tests.
    // Doing it like this ensures that we can make sure that the source we pass is the file based one,
    // and only when it's ready.
    def stream(
      block: R[Source[ByteString, _]] => Future[Result]
    )(implicit temporaryFileCreator: TemporaryFileCreator): Action[Source[ByteString, _]] =
      actionBuilder.async(streamFromMemory) {
        request =>
          val file = temporaryFileCreator.create()
          (for {
            _      <- request.body.runWith(FileIO.toPath(file))
            result <- block(request.replaceBody(FileIO.fromPath(file)))
          } yield result)
            .attemptTap {
              _ =>
                file.delete()
                Future.successful(())
            }
      }
  }

  private val START_TOKEN: Source[ByteString, NotUsed]      = Source.single(ByteString("{"))
  private val END_TOKEN_SOURCE: Source[ByteString, NotUsed] = Source.single(ByteString("}"))

  def mergeStreamIntoJson(fields: collection.Seq[(String, JsValue)], fieldName: String, stream: Source[ByteString, _]): Source[ByteString, _] =
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
        bs => ByteString(bs.utf8String.replace("\\", "\\\\").replace("\"", "\\\""))
      ) ++
      // and the stream that ends our Json document
      END_TOKEN_SOURCE

  def jsonToByteStringStream(fields: collection.Seq[(String, JsValue)]) =
    START_TOKEN ++ Source
      .fromIterator(
        () => fields.iterator
      )
      .map {
        // convert fields to bytestrings with their values, need to ensure we keep the field names quoted,
        // separate with a colon and end with a comma, as per the Json spec
        tuple =>
          ByteString(s""""${tuple._1}":${Json.stringify(tuple._2)}""")
      }
      .intersperse(ByteString(",")) ++ END_TOKEN_SOURCE
}
