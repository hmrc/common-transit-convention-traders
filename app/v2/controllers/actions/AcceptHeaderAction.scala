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

package v2.controllers.actions

import com.google.inject.Inject
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.ActionRefiner
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results.NotAcceptable
import routing.VersionedRouting
import v2.models.errors.PresentationError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait AcceptHeaderAction[R[_] <: Request[_]] extends ActionRefiner[R, R]

class AcceptHeaderActionImpl[R[_] <: Request[_]] @Inject() (acceptedHeaders: Seq[String])(implicit
  val executionContext: ExecutionContext
) extends AcceptHeaderAction[R] {

  override protected def refine[A](request: R[A]): Future[Either[Result, R[A]]] =
    request.headers.get(HeaderNames.ACCEPT) match {
      case Some(value) => Future.successful(checkAcceptHeader(value, request))
      case None =>
        Future.successful(
          Left(
            NotAcceptable(
              Json.toJson(
                PresentationError.notAcceptableError("The Accept header is missing or invalid.")
              )
            )
          )
        )
    }

  private def checkAcceptHeader[A](acceptHeaderValue: String, request: R[A]): Either[Result, R[A]] =
    if (acceptedHeaders.contains(VersionedRouting.formatAccept(acceptHeaderValue))) Right(request)
    else Left(NotAcceptable(Json.toJson(PresentationError.notAcceptableError("The Accept header is missing or invalid."))))

}
