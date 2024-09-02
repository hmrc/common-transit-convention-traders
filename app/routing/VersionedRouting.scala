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

import scala.concurrent.Future
import scala.util.matching.Regex

sealed trait VersionedAcceptHeader {
  val value: String
}

object VersionedAcceptHeader {

  def apply(value: String): Either[PresentationError, VersionedAcceptHeader] = value match {
    case VERSION_2_ACCEPT_HEADER_VALUE_XML.value               => VERSION_2_ACCEPT_HEADER_VALUE_XML.asRight
    case VERSION_2_ACCEPT_HEADER_VALUE_JSON.value              => VERSION_2_ACCEPT_HEADER_VALUE_JSON.asRight
    case VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML.value          => VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML.asRight
    case VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.value   => VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.asRight
    case VERSION_2_1_ACCEPT_HEADER_VALUE_XML.value             => VERSION_2_1_ACCEPT_HEADER_VALUE_XML.asRight
    case VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value            => VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.asRight
    case VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML.value        => VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML.asRight
    case VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.value => VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.asRight
    case invalidAcceptHeader                                   => PresentationError.notAcceptableError(s"Invalid accept header: $invalidAcceptHeader").asLeft
  }
}

case object VERSION_2_ACCEPT_HEADER_VALUE_XML extends VersionedAcceptHeader {
  override val value: String = "application/vnd.hmrc.2.0+xml"
}

case object VERSION_2_ACCEPT_HEADER_VALUE_JSON extends VersionedAcceptHeader {
  override val value: String = "application/vnd.hmrc.2.0+json"
}

case object VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML extends VersionedAcceptHeader {
  override val value: String = "application/vnd.hmrc.2.0+json+xml"
}

case object VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN extends VersionedAcceptHeader {
  override val value: String = "application/vnd.hmrc.2.0+json-xml"
}

case object VERSION_2_1_ACCEPT_HEADER_VALUE_XML extends VersionedAcceptHeader {
  override val value: String = "application/vnd.hmrc.2.1+xml"
}

case object VERSION_2_1_ACCEPT_HEADER_VALUE_JSON extends VersionedAcceptHeader {
  override val value: String = "application/vnd.hmrc.2.1+json"
}

case object VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML extends VersionedAcceptHeader {
  override val value: String = "application/vnd.hmrc.2.1+json+xml"
}

case object VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN extends VersionedAcceptHeader {
  override val value: String = "application/vnd.hmrc.2.1+json-xml"
}

object VersionedRouting {
  val VERSION_2_ACCEPT_HEADER_PATTERN: Regex   = """^application/vnd\.hmrc\.2\.0\+.+$""".r
  val VERSION_2_1_ACCEPT_HEADER_PATTERN: Regex = """^application/vnd\.hmrc\.2\.1\+.+$""".r

  def formatAccept(header: String): Either[PresentationError, VersionedAcceptHeader] = {
    val version2SplitHeader   = """^(application/vnd\.hmrc\.2\.0\+)(.+)""".r
    val version2_1SplitHeader = """^(application/vnd\.hmrc\.2\.1\+)(.+)""".r

    header match {
      case version2SplitHeader(frameworkPath, ctcPath) =>
        val formattedHeader = frameworkPath + ctcPath.toLowerCase
        VersionedAcceptHeader(formattedHeader)
      case version2_1SplitHeader(frameworkPath, ctcPath) =>
        val formattedHeader = frameworkPath + ctcPath.toLowerCase
        VersionedAcceptHeader(formattedHeader)
      case invalidHeader => PresentationError.notAcceptableError(s"Invalid accept header: $invalidHeader").asLeft
    }
  }
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

  def handleDisabledPhase5Transitional(): Action[Source[ByteString, _]] =
    Action(streamFromMemory) {
      request =>
        request.body.runWith(Sink.ignore)
        val presentationError = PresentationError.notAcceptableError(
          "CTC Traders API version 2.0 is no longer available. Use CTC Traders API v2.1 to submit transit messages."
        )
        Status(presentationError.code.statusCode)(Json.toJson(presentationError))
    }

  def handleDisabledPhase5Final(): Action[Source[ByteString, _]] =
    Action(streamFromMemory) {
      request =>
        request.body.runWith(Sink.ignore)
        val presentationError = PresentationError.notAcceptableError(
          "CTC Traders API version 2.1 is not available. Use CTC Traders API v2.0 to submit transit messages."
        )
        Status(presentationError.code.statusCode)(Json.toJson(presentationError))
    }
}
