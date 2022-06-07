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

package v2.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import cats.syntax.all._
import play.api.libs.Files
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results.Status
import v2.models.errors.PresentationError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

trait TemporaryFiles {
  val temporaryFileCreator: TemporaryFileCreator

  def withTemporaryFile[R](
    onSucceed: (Files.TemporaryFile, Source[ByteString, _]) => Future[R]
  )(implicit request: Request[Source[ByteString, _]], materializer: Materializer, ec: ExecutionContext): EitherT[Future, Throwable, R] =
    EitherT(Future.successful(Try(temporaryFileCreator.create()).toEither))
      .leftSemiflatTap {
        _ =>
          request.body.runWith(Sink.ignore)
      }
      .flatMap {
        temporaryFile =>
          // As well as sending this stream to another service, we need to save it as
          // if we succeed in validating, we will want to run the stream again to other
          // services - saving it to file means we can keep it out of memory.
          //
          // The alsoTo call causes the file to be written as we send the request -
          // fanning-out such that we request and save at the same time.
          val source = request.body.alsoTo(FileIO.toPath(temporaryFile.path))
          EitherT.right(
            onSucceed(temporaryFile, source).flatTap(
              _ => Future.successful(temporaryFile.delete())
            )
          )
      }

  implicit class TemporaryFileResult(value: EitherT[Future, Throwable, Result]) {

    def toResult(implicit ec: ExecutionContext): Future[Result] =
      value
        .leftMap(
          thr => PresentationError.internalServiceError(cause = Some(thr))
        )
        .fold(presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)), in => in)
  }

}
