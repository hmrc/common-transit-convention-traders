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

import cats.implicits.catsSyntaxEitherId
import controllers.common.stream.StreamingParsers
import models.MediaType
import models.VersionedHeader
import models.Version
import models.common.errors.PresentationError
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.http.HeaderNames
import play.api.http.HttpVerbs
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.PathBindable
import play.api.mvc.Request
import play.api.mvc.Result

import scala.concurrent.Future
import scala.util.matching.Regex

object VersionedRouting {
  val versionedRegex: Regex = """^application/vnd\.hmrc\.(\d*\.\d*)\+(.+)""".r

  def validateAcceptHeader(header: String): Either[PresentationError, VersionedHeader] =
    header match {
      case versionedRegex(ver, ext) =>
        for {
          extension <- MediaType.fromString(ext)
          version   <- Version.fromString(ver)
          result    <- VersionedHeader.fromExtensionAndVersion(extension, version)
        } yield result
      case invalidHeader => PresentationError.notAcceptableError(s"Invalid accept header: $invalidHeader").asLeft
    }
}

trait VersionedRouting {
  self: BaseController & StreamingParsers =>

  private def handleVerbs(request: Request[Source[ByteString, ?]], action: Action[?]): Future[Result] =
    request.method match {
      case HttpVerbs.GET | HttpVerbs.HEAD | HttpVerbs.DELETE | HttpVerbs.OPTIONS =>
        request.body.to(Sink.ignore).run()
        val headersWithoutContentType = request.headers.remove(CONTENT_TYPE)
        action(request.withHeaders(headersWithoutContentType)).run()
      case _ =>
        action(request).run(request.body)
    }

  def route(routes: PartialFunction[Unit, Action[?]])(implicit materializer: Materializer): Action[Source[ByteString, ?]] =
    Action.async(streamFromMemory) {
      (request: Request[Source[ByteString, ?]]) =>
        routes
          .lift(request.headers.get(HeaderNames.ACCEPT))
          .map(handleVerbs(request, _))
          .map {
            run =>
              val maybeAcceptHeader = request.headers.get(HeaderNames.ACCEPT)
              maybeAcceptHeader match {
                case Some(VersionedRouting.versionedRegex(_, _)) => run
                case _ =>
                  request.body.runWith(Sink.ignore)
                  val presentationError = PresentationError.notAcceptableError(
                    "Use CTC Traders API v2.1 to submit transit messages."
                  )
                  Future.successful(Status(presentationError.code.statusCode)(Json.toJson(presentationError)))
              }
          }
          .getOrElse {
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

  private def bindingFailureAction(message: String)(implicit materializer: Materializer): Action[Source[ByteString, ?]] =
    Action.async(streamFromMemory) {
      implicit request =>
        request.body.runWith(Sink.ignore)
        Future.successful(Status(BAD_REQUEST)(Json.toJson(PresentationError.bindingBadRequestError(message))))
    }

  def runIfBound[A](key: String, value: String, action: A => Action[?])(implicit binding: PathBindable[A]): Action[?] =
    binding.bind(key, value).fold(bindingFailureAction(_), action)
}
