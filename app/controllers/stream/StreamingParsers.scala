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

package controllers.stream

import akka.stream.IOResult
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.Files
import play.api.libs.streams.Accumulator
import play.api.mvc.BaseControllerHelpers
import play.api.mvc.BodyParser
import play.api.mvc.Request

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait StreamingParsers {
  self: BaseControllerHelpers =>

  implicit val materializer: Materializer

  // TODO: do we choose a better thread pool, or make configurable?
  //  We have to be careful to not use Play's EC because we could accidentally starve the thread pool
  //  and cause errors for additional connections
  implicit val materializerExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  def streamFromTemporaryFile[A](block: Source[ByteString, Future[IOResult]] => RunnableGraph[Future[A]])(implicit
    request: Request[Files.TemporaryFile]
  ): Future[A] =
    block(FileIO.fromPath(request.body.path)).run()

  lazy val streamFromMemory: BodyParser[Source[ByteString, _]] = BodyParser {
    _ =>
      Accumulator.source[ByteString].map(Right.apply)
  }

  lazy val streamFromFile: BodyParser[Source[ByteString, _]] = parse.temporaryFile.map {
    tempFile =>
      FileIO.fromPath(tempFile.path)
  }

  lazy val streamIntelligently: BodyParser[Source[ByteString, _]] = streamIntelligently(100000)

  def streamIntelligently(maxLengthInMemory: Long): BodyParser[Source[ByteString, _]] = parse.using {
    headers =>
      if (headers.headers.get(CONTENT_LENGTH).map(_.toLong).exists(_ < maxLengthInMemory)) streamFromMemory
      else streamFromFile
  }

}
