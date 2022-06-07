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

package routing

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.Request
import v2.controllers.stream.StreamingParsers
import v2.models.errors.PresentationError

import scala.concurrent.Future

trait VersionedRouting {
  self: BaseController with StreamingParsers =>

  def route(routes: PartialFunction[Option[String], Action[_]])(implicit materializer: Materializer): Action[Source[ByteString, _]] =
    Action.async(streamFromMemory) {
      (request: Request[Source[ByteString, _]]) =>
        routes
          .lift(request.headers.get("accept"))
          .map(
            action => action(request).run(request.body)
          )
          .getOrElse {
            // To avoid a memory leak, we need to ensure we run the request stream and ignore it.
            request.body.to(Sink.ignore).run()
            Future.successful(
              UnsupportedMediaType(
                Json.toJson(
                  PresentationError.unsupportedMediaTypeError(
                    request.headers
                      .get("accept")
                      .map(
                        header => s"Accept header $header is not supported!"
                      )
                      .getOrElse("An accept header is required!")
                  )
                )
              )
            )
          }
    }

}
