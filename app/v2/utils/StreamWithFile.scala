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

package v2.utils

import akka.stream.IOResult
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.Monad
import cats.data.EitherT
import cats.syntax.flatMap._
import cats.syntax.functor._
import play.api.libs.Files.TemporaryFileCreator
import v2.models.errors.PresentationError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

trait StreamWithFile {

  type PresentationErrorEitherT[A] = EitherT[Future, PresentationError, A]

  implicit val futureStream = new StreamWithFileMonad[Future] {

    override def wrapSource(sourceFuture: Future[IOResult])(implicit ec: ExecutionContext): Future[IOResult] = sourceFuture
  }

  implicit val eitherTStream = new StreamWithFileMonad[PresentationErrorEitherT] {

    override def wrapSource(sourceFuture: Future[IOResult])(implicit ec: ExecutionContext): PresentationErrorEitherT[IOResult] =
      EitherT(
        sourceFuture
          .map(Right.apply)
          .recover {
            case NonFatal(thr) => Left(PresentationError.internalServiceError(cause = Some(thr)))
          }
      )
  }

  def withReusableSource[M[_], A](
    src: Source[ByteString, _]
  )(
    block: Source[ByteString, _] => M[A]
  )(implicit temporaryFileCreator: TemporaryFileCreator, mat: Materializer, ec: ExecutionContext, ev: StreamWithFileMonad[M], monad: Monad[M]): M[A] = {
    val file = temporaryFileCreator.create()
    (for {
      _      <- ev.wrapSource(src.runWith(FileIO.toPath(file)))
      result <- block(FileIO.fromPath(file))
    } yield result)
      .flatTap {
        _ =>
          file.delete()
          monad.pure(())
      }

  }

}
