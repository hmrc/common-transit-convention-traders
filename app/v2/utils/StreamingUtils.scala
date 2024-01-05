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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.data.EitherT
import v2.models.errors.StreamingError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

object StreamingUtils {

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
}
