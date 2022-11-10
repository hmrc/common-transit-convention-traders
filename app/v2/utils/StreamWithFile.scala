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

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.implicits.catsSyntaxMonadError
import play.api.libs.Files.TemporaryFileCreator

import java.nio.file.Path
import java.nio.file.StandardOpenOption
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

trait StreamWithFile {

  def withReusableSource[R: FutureConversion](
    src: Source[ByteString, _]
  )(block: Source[ByteString, _] => R)(implicit temporaryFileCreator: TemporaryFileCreator, mat: Materializer, ec: ExecutionContext): R = {
    val file   = temporaryFileCreator.create()
    val source = createSource(file.path, src)
    val result = block(source)

    // convert to a future to do cleanup after -- this can be done async so we just get the
    // future out
    implicitly[FutureConversion[R]]
      .toFuture(result)
      .recoverWith { // acts as a tap
        case NonFatal(thr) =>
          source.runWith(Sink.ignore)
          Future.failed(thr)
      }
      .attemptTap {
        _ =>
          file.delete()
          Future.successful(())
      }
    result // as cleanup can be done async, we just return this result.
  }

  private def createSource(path: Path, primary: Source[ByteString, _])(implicit mat: Materializer): Source[ByteString, _] = {
    val preMat = FileIO.toPath(path, Set(StandardOpenOption.WRITE)).preMaterialize()
    primary
      .alsoTo(preMat._2)
      .via(
        OrElseOnCancel.orElseOrCancelGraph(
          // This is trying to create a stream that backpressures until the future from the pre-materialisation
          // (above) is cleared. preMat will complete when the file write is completed the first time.
          // Then, we throw away the value of the object, and concatenate with the second object
          // which will then read from the freshly written file. If the future is already completed,
          // the file read will occur instantly.
          Source
            .future(preMat._1)
            .mapConcat(
              _ => Seq[ByteString]()
            ) ++ FileIO.fromPath(path)
        )
      )
  }

}
