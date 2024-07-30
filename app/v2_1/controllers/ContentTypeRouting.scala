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

package v2_1.controllers

import models.common.errors.PresentationError
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.Request
import stream.StreamingParsers

import scala.concurrent.Future

object ContentTypeRouting {

  sealed abstract class ContentType

  object ContentType {
    final case object XML extends ContentType

    final case object JSON extends ContentType

    final case object None extends ContentType
  }

  // The actual content-type won't change, so we cache it in memory once it's needed.
  lazy val XML_UTF8_CHARSET: String = ContentTypes.XML.toLowerCase

}

trait ContentTypeRouting {
  self: BaseController with StreamingParsers =>

  def selectContentType(contentType: Option[String]): Option[ContentTypeRouting.ContentType] =
    contentType.map(_.toLowerCase) match {
      case Some(MimeTypes.XML | ContentTypeRouting.XML_UTF8_CHARSET) => Some(ContentTypeRouting.ContentType.XML)
      case Some(MimeTypes.JSON)                                      => Some(ContentTypeRouting.ContentType.JSON)
      case Some(_)                                                   => None
      case None                                                      => Some(ContentTypeRouting.ContentType.None)
    }

  def contentTypeRoute(routes: PartialFunction[ContentTypeRouting.ContentType, Action[_]])(implicit materializer: Materializer): Action[Source[ByteString, _]] =
    Action.async(streamFromMemory) {
      (request: Request[Source[ByteString, _]]) =>
        selectContentType(request.headers.get(HeaderNames.CONTENT_TYPE))
          .flatMap {
            contentType =>
              routes
                .lift(contentType)
                .map(
                  action => action(request).run(request.body)
                )
          }
          .getOrElse {
            // To avoid a memory leak, we need to ensure we run the request stream and ignore it.
            request.body.to(Sink.ignore).run()
            Future.successful(
              UnsupportedMediaType(
                Json.toJson(
                  PresentationError.unsupportedMediaTypeError(
                    request.headers
                      .get(HeaderNames.CONTENT_TYPE)
                      .map(
                        headerValue => s"Content-type header $headerValue is not supported!"
                      )
                      .getOrElse("A content-type header is required!")
                  )
                )
              )
            )
          }
    }

}
