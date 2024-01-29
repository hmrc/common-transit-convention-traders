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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.data.EitherT
import cats.implicits.catsSyntaxMonadError
import play.api.Logging
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc._
import v2.controllers.request.BodyReplaceableRequest
import v2.models.errors.PresentationError

import java.nio.file.Files
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

trait StreamingParsers {
  self: BaseControllerHelpers with Logging =>

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
      streamWithSize(
        request => _ => block(request)
      )

    def streamWithSize(
      block: R[Source[ByteString, _]] => Long => Future[Result]
    )(implicit temporaryFileCreator: TemporaryFileCreator): Action[Source[ByteString, _]] =
      actionBuilder.async(streamFromMemory) {
        request =>
          // This is outside the for comprehension because we need access to the file
          // if the rest of the futures fail, which we wouldn't get if it was in there.
          Future
            .fromTry(Try(temporaryFileCreator.create()))
            .flatMap {
              file =>
                request.body
                  .runWith(FileIO.toPath(file))
                  .flatMap(
                    _ => calculateResult(file, block(request.replaceBody(FileIO.fromPath(file))))
                  )
                  .attemptTap {
                    _ =>
                      file.delete()
                      Future.successful(())
                  }
            }
            .recover {
              case NonFatal(ex) =>
                logger.error(s"Failed call: ${ex.getMessage}", ex)
                Status(INTERNAL_SERVER_ERROR)(Json.toJson(PresentationError.internalServiceError(cause = Some(ex))))
            }
      }

    private def calculateResult(file: play.api.libs.Files.TemporaryFile, block: Long => Future[Result]): Future[Result] =
      for {
        size   <- Future.fromTry(Try(Files.size(file)))
        result <- block(size)
      } yield result
  }

  private val START_TOKEN: Source[ByteString, NotUsed]                 = Source.single(ByteString("{"))
  private val END_TOKEN_SOURCE: Source[ByteString, NotUsed]            = Source.single(ByteString("}"))
  private val END_TOKEN_WITH_QUOTE_SOURCE: Source[ByteString, NotUsed] = Source.single(ByteString("\"}"))
  private val COMMA_SOURCE: Source[ByteString, NotUsed]                = Source.single(ByteString(","))

  private def jsonFieldsToByteString(fields: collection.Seq[(String, JsValue)]) =
    Source
      .fromIterator(
        () => fields.iterator
      )
      .map {
        // convert fields to bytestrings with their values, need to ensure we keep the field names quoted,
        // separate with a colon and end with a comma, as per the Json spec
        tuple =>
          ByteString(s""""${tuple._1}":${Json.stringify(tuple._2)}""")
      }
      .intersperse(ByteString(","))

  def mergeStreamIntoJson(
    fields: collection.Seq[(String, JsValue)],
    fieldName: String,
    stream: Source[ByteString, _]
  ): EitherT[Future, PresentationError, Source[ByteString, _]] =
    EitherT {
      Future
        .successful(
          Right( // We need to start the document and add the final field, and prepare for the string data coming in
            START_TOKEN ++
              // convert fields to bytestrings
              jsonFieldsToByteString(fields) ++ COMMA_SOURCE ++
              // start adding the new field
              Source.single(ByteString(s""""$fieldName":"""".stripMargin)) ++
              // our XML stream that we escape
              stream
                .map(
                  bs => ByteString(bs.utf8String.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"))
                )
              ++
              // and the stream that ends our Json document
              END_TOKEN_WITH_QUOTE_SOURCE
          )
        )
        .recover {
          case NonFatal(e) =>
            logger.error(s"Merge Stream got failed: ${e.getMessage}", e)
            Left(PresentationError.internalServiceError(cause = Some(e)))
        }
    }

  def jsonToByteStringStream(
    fields: collection.Seq[(String, JsValue)],
    fieldName: String,
    stream: Source[ByteString, _]
  ): EitherT[Future, PresentationError, Source[ByteString, _]] =
    EitherT {
      Future
        .successful(
          Right(
            START_TOKEN ++ jsonFieldsToByteString(fields) ++ COMMA_SOURCE ++ Source.single(
              ByteString(s""""$fieldName":""".stripMargin)
            ) ++ stream ++ END_TOKEN_SOURCE
          )
        )
        .recover {
          case NonFatal(e) =>
            logger.error(s"Json to Byte String got failed: ${e.getMessage}", e)
            Left(PresentationError.internalServiceError(cause = Some(e)))
        }
    }

  def stringToByteStringStream(value: String): EitherT[Future, PresentationError, Source[ByteString, _]] =
    EitherT {
      Future
        .successful(Right(Source.single(value) map {
          str => ByteString(str)
        }))
        .recover {
          case NonFatal(e) =>
            logger.error(s"String to Byte String got failed: ${e.getMessage}", e)
            Left(PresentationError.internalServiceError(cause = Some(e)))
        }
    }
}
