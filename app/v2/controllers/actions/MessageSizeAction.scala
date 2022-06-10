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

package v2.controllers.actions

import com.google.inject.Inject
import config.AppConfig
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.ActionRefiner
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results.BadRequest
import play.api.mvc.Results.EntityTooLarge
import v2.models.errors.PresentationError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

trait MessageSizeAction[R[_] <: Request[_]] extends ActionRefiner[R, R]

class MessageSizeActionImpl[R[_] <: Request[_]] @Inject() (config: AppConfig)(implicit val executionContext: ExecutionContext) extends MessageSizeAction[R] {

  override protected def refine[A](request: R[A]): Future[Either[Result, R[A]]] =
    contentLengthHeader(request) match {
      case Some((_, length)) => Future.successful(checkSize(length, request))
      case None =>
        Future.successful(Left(BadRequest(Json.toJson(PresentationError.badRequestError("Missing content-length header"))))) // should never happen
    }

  private def checkSize[A](length: String, request: R[A]): Either[Result, R[A]] =
    Try(length.toInt) match {
      case Success(x) if x <= limit => Right(request)
      case Success(_)               => Left(EntityTooLarge(Json.toJson(PresentationError.entityTooLargeError(s"Your message size must be less than $limit bytes"))))
      case Failure(_)               => Left(BadRequest(Json.toJson(PresentationError.badRequestError("Invalid content-length value"))))
    }

  private def contentLengthHeader[A](request: R[A]): Option[(String, String)] =
    request.headers.headers.find(_._1.equalsIgnoreCase(HeaderNames.CONTENT_LENGTH))

  private lazy val limit = config.messageSizeLimit
}
