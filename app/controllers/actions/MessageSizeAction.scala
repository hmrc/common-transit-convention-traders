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

package controllers.actions

import com.google.inject.Inject
import config.AppConfig
import models.errors._
import models.formats.HttpFormats
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.Results.{BadRequest, EntityTooLarge}
import play.api.mvc.{ActionRefiner, Request, Result}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


class MessageSizeAction @Inject() (config: AppConfig)(implicit val executionContext: ExecutionContext)
        extends ActionRefiner[Request, Request]
        with HttpFormats {

  override protected def refine[A](request: Request[A]): Future[Either[Result, Request[A]]] = {
    contentLengthHeader(request) match {
      case Some((_, length)) => Future.successful(checkSize(length, request))
      case None              => Future.successful(Left(BadRequest(Json.toJson[TransitMovementError](BadRequestError("Missing content-length header"))))) // should never happen
    }
  }

  private def checkSize[A](length: String, request: Request[A]): Either[Result, Request[A]] = {
    Try(length.toInt).toOption match {
      case Some(x) if x <= limit  => Right(request)
      case Some(_)                => Left(EntityTooLarge(Json.toJson[TransitMovementError](EntityTooLargeError(s"Your message size must be less than $limit bytes"))))
      case None                   => Left(BadRequest(Json.toJson[TransitMovementError](BadRequestError("Invalid content-length value"))))
    }
  } 

  private def contentLengthHeader[A](request: Request[A]): Option[(String,String)] =
    request.headers.headers.find(_._1.equalsIgnoreCase(HeaderNames.CONTENT_LENGTH))

  private lazy val limit = config.messageSizeLimit
}
