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

import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.data.EitherT
import cats.syntax.flatMap._
import play.api.Logging
import play.api.libs.Files.TemporaryFileCreator
import v2.models.errors.PresentationError

import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.Try

trait StreamWithFile {
  self: Logging =>

  def withReusableSource[R](
    src: Source[ByteString, _]
  )(
    block: Source[ByteString, _] => EitherT[Future, PresentationError, R]
  )(implicit temporaryFileCreator: TemporaryFileCreator, mat: Materializer, ec: ExecutionContext): EitherT[Future, PresentationError, R] =
    withReusableSourceAndSize(src)(
      (fileSource, _) => block(fileSource)
    )

  def withReusableSourceAndSize[R](
    src: Source[ByteString, _]
  )(
    block: (Source[ByteString, _], Long) => EitherT[Future, PresentationError, R]
  )(implicit temporaryFileCreator: TemporaryFileCreator, mat: Materializer, ec: ExecutionContext): EitherT[Future, PresentationError, R] = {
    val file = temporaryFileCreator.create()
    (for {
      _      <- writeToFile(file, src)
      size   <- calculateSize(file)
      result <- block(FileIO.fromPath(file), size)
    } yield result)
      .flatTap {
        _ =>
          file.delete()
          EitherT.rightT(())
      }

  }

  private def calculateSize(file: Path): EitherT[Future, PresentationError, Long] =
    EitherT(
      Future.fromTry(
        Try(Files.size(file))
          .map(Right.apply)
          .recover {
            case NonFatal(e) =>
              logger.error(s"Exception occurred while calculating size of payload ${e.getMessage}", e)
              Left(PresentationError.internalServiceError(cause = Some(e)))
          }
      )
    )

  private def writeToFile(file: Path, src: Source[ByteString, _])(implicit
    mat: Materializer,
    ec: ExecutionContext
  ): EitherT[Future, PresentationError, IOResult] =
    EitherT(
      src
        .runWith(FileIO.toPath(file))
        .map(Right.apply)
        .recover {
          case NonFatal(thr) =>
            logger.error(s"Failed to create file stream: $thr", thr)
            Left(PresentationError.internalServiceError(cause = Some(thr)))
        }
    )

}
