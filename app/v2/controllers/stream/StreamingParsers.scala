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

  // TODO: do we choose a better thread pool, or make configurable?
  //  We have to be careful to not use Play's EC because we could accidentally starve the thread pool
  //  and cause errors for additional connections
  implicit val materializerExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  lazy val streamFromMemory: BodyParser[Source[ByteString, _]] = BodyParser {
    _ =>
      Accumulator.source[ByteString].map(Right.apply)
  }

  implicit class ActionBuilderStreamHelpers[R[A] <: BodyReplaceableRequest[R, A]](actionBuilder: ActionBuilder[R, _]) {

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
