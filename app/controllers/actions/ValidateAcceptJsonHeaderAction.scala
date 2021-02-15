/*
 * Copyright 2021 HM Revenue & Customs
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

import config.Constants

import javax.inject.Inject
import play.api.http.Status.NOT_ACCEPTABLE
import play.api.libs.json.Json
import play.api.mvc.Results.NotAcceptable
import play.api.mvc.{ActionRefiner, Request, Result}
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse

import scala.concurrent.{ExecutionContext, Future}

class ValidateAcceptJsonHeaderAction @Inject()()(
  implicit val executionContext: ExecutionContext)
  extends ActionRefiner[Request, Request] {

  override protected def refine[A](request: Request[A]): Future[Either[Result, Request[A]]] = {
    request.headers.get("Accept") match {
      case Some(value) =>
        value match {
          case Constants.AcceptHeaderPattern(version, contentType) =>
            (version, contentType) match {
              case ("1.0", "json") =>
                Future.successful(Right(request))
              case _ =>
                val response = ErrorResponse(NOT_ACCEPTABLE, Constants.AcceptHeaderMissing)
                Future.successful(Left(NotAcceptable(Json.toJson(response))))
            }
          case _ =>
            val response = ErrorResponse(NOT_ACCEPTABLE, Constants.AcceptHeaderMissing)
            Future.successful(Left(NotAcceptable(Json.toJson(response))))
        }
      case None =>
        val response = ErrorResponse(NOT_ACCEPTABLE, Constants.AcceptHeaderMissing)
        Future.successful(Left(NotAcceptable(Json.toJson(response))))
    }
  }
}
