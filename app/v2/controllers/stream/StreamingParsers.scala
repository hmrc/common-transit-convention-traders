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

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.streams.Accumulator
import play.api.mvc.Action
import play.api.mvc.ActionBuilder
import play.api.mvc.BaseControllerHelpers
import play.api.mvc.BodyParser
import play.api.mvc.Result
import v2.controllers.request.BodyReplaceableRequest
import v2.models.errors.PresentationError
import v2.utils.FutureConversions
import v2.utils.StreamWithFile

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

trait StreamingParsers extends StreamWithFile with FutureConversions {
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
      *   If your first use of the stream may not require the whole stream, you should use
      *   [[streamWithAwait]] instead.
      *
      *   @param block The code to use the with the resulable source
      *   @param temporaryFileCreator The [[TemporaryFileCreator]] to use
      *   @return An [[Action]]
      */
    def stream(
      block: R[Source[ByteString, _]] => Future[Result]
    )(implicit temporaryFileCreator: TemporaryFileCreator): Action[Source[ByteString, _]] =
      actionBuilder.async(streamFromMemory) {
        request =>
          withReusableSource(request.body) {
            memoryOrFileSource =>
              block(request.replaceBody(memoryOrFileSource))
          }
      }

    /** Updates the [[Source]] in the [[BodyReplaceableRequest]] with a version that can be used
      *  multiple times via the use of a temporary file, as well as an [[EitherT]] that can be
      *  used to determine whether the file write after the first stream has completed.
      *
      *  You may wish to use the awaiting future if your first use of the stream may complete
      *  before the end of the stream is reached, for example, if the stream is Json and you
      *  wish to get something early on in the stream. Akka will return a completed future with
      *  this information as soon as it can, but this may be while the stream is still streaming
      *  to a file -- and so using the stream again in another request may cause issues, such as
      *  starting a new request to another service but delaying sending the body, which might
      *  case the consuming service to timeout waiting for the body and proceed as if there was
      *  no body.
      *
      *  For example, you might do something like this to ensure doSomething2 only runs when the
      *  stream is now completely written to a file.
      *
      *  {{{
      *    Action.streamWithAwait {
      *    awaitFileWrite => implicit request =>
      *      (for {
      *          result1 <- doSomething()
      *          _       <- awaitFileWrite
      *          result2 <- doSomething2(result1)
      *      } yield result2)
      *    }
      *  }}}
      *
      *  If your first use of the stream would require the whole stream, you should use
      *  [[stream]] instead as you don't need to await in this scenario.
      *
      *  See the code in [[withReusableSourceAndAwaiter]] for the implementation details.
      *
      *  @param block A curried function that provides a [[Future]] that can be used to await the
      *               completion of the first use of the stream, signalling that the file write
      *               has completed and the stream can be used a second time safely, and the request.
      *  @param temporaryFileCreator The [[TemporaryFileCreator]] to use
      *  @return An [[Action]]
      */
    def streamWithAwait(
      block: EitherT[Future, PresentationError, Unit] => R[Source[ByteString, _]] => Future[Result]
    )(implicit temporaryFileCreator: TemporaryFileCreator): Action[Source[ByteString, _]] =
      actionBuilder.async(streamFromMemory) {
        request =>
          withReusableSourceAndAwaiter(request.body) {
            (memoryOrFileSource, await) =>
              block(awaitAsEitherT(await))(request.replaceBody(memoryOrFileSource))
          }
      }

    private def awaitAsEitherT(future: Future[_]): EitherT[Future, PresentationError, Unit] =
      EitherT {
        future
          .map(
            _ => Right(())
          )
          .recover {
            case NonFatal(e) => Left(PresentationError.internalServiceError(cause = Some(e)))
          }
      }

  }

}
