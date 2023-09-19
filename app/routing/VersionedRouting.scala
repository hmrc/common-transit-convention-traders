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

package routing

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.http.HeaderNames
import play.api.http.HttpVerbs
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.PathBindable
import play.api.mvc.Request
import v2.controllers.stream.StreamingParsers
import v2.models.errors.PresentationError

import scala.concurrent.Future

object VersionedRouting {

  val VERSION_2_ACCEPT_HEADER_VALUE_XML             = "application/vnd.hmrc.2.0+xml"                 // returns XML only
  val VERSION_2_ACCEPT_HEADER_VALUE_JSON            = "application/vnd.hmrc.2.0+json"                // returns JSON only
  val VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML        = "application/vnd.hmrc.2.0+json+xml"            // returns JSON wrapped XML
  val VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN = "application/vnd.hmrc.2.0+json-xml"            // returns JSON wrapped XML
  val VERSION_2_ACCEPT_HEADER_PATTERN               = """(?i)^application\/vnd\.hmrc\.2\.0\+.+$""".r // (?i) - case insensitive-mode

}

trait VersionedRouting {
  self: BaseController with StreamingParsers =>

  def route(routes: PartialFunction[Option[String], Action[_]])(implicit materializer: Materializer): Action[Source[ByteString, _]] =
    Action.async(streamFromMemory) {
      (request: Request[Source[ByteString, _]]) =>
        routes
          .lift(request.headers.get(HeaderNames.ACCEPT))
          .map {
            action =>
              request.method match {
                case HttpVerbs.GET | HttpVerbs.HEAD | HttpVerbs.DELETE | HttpVerbs.OPTIONS =>
                  // For the above verbs, we don't want to send a body,
                  // however, in case we have one, we still need to drain the body.
                  request.body.to(Sink.ignore).run()

                  // We need to remove this as it might otherwise trigger any AnyContent
                  // actions to try to parse an empty body
                  val headersWithoutContentType = request.headers.remove(CONTENT_TYPE)
                  action(request.withHeaders(headersWithoutContentType)).run()
                case _ =>
                  action(request).run(request.body)
              }
          }
          .getOrElse {
            // To avoid a memory leak, we need to ensure we run the request stream and ignore it.
            request.body.to(Sink.ignore).run()
            Future.successful(
              NotAcceptable(
                Json.toJson(
                  PresentationError.notAcceptableError("The Accept header is missing or invalid.")
                )
              )
            )
          }
    }

  // This simulates what Play will do if a binding fails, with the addition of the "code" field
  // that we use elsewhere.
  def bindingFailureAction(message: String)(implicit materializer: Materializer): Action[Source[ByteString, _]] =
    Action.async(streamFromMemory) {
      implicit request =>
        request.body.runWith(Sink.ignore)
        Future.successful(Status(BAD_REQUEST)(Json.toJson(PresentationError.bindingBadRequestError(message))))
    }

  def runIfBound[A](key: String, value: String, action: A => Action[_])(implicit binding: PathBindable[A]): Action[_] =
    binding.bind(key, value).fold(bindingFailureAction(_), action)

}
