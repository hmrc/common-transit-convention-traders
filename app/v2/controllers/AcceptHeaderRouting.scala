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
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results.NotAcceptable
import v2.models.errors.PresentationError

import scala.concurrent.Future

trait AcceptHeaderRouting {

  def acceptHeaderRoute(
    routes: PartialFunction[Option[String], Future[Result]]
  )(implicit materializer: Materializer, request: Request[_]): Future[Result] =
    routes
      .lift(request.headers.get(HeaderNames.ACCEPT))
      .getOrElse {
        Future.successful(
          NotAcceptable(
            Json.toJson(
              PresentationError.notAcceptableError(
                request.headers
                  .get(HeaderNames.ACCEPT)
                  .map(
                    headerValue => s"Accept header $headerValue is not supported!"
                  )
                  .getOrElse("Accept header is required!")
              )
            )
          )
        )
      }

}
