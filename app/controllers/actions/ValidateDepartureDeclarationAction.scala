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

package controllers.actions

import javax.inject.Inject
import models.request.DepartureDeclarationXSD
import play.api.mvc.Results.BadRequest
import play.api.mvc.ActionRefiner
import play.api.mvc.Request
import play.api.mvc.Result
import services.XmlError
import services.XmlValidationService

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.xml.NodeSeq

class ValidateDepartureDeclarationAction[R[_] <: Request[_]] @Inject() (xmlValidationService: XmlValidationService)(implicit
  val executionContext: ExecutionContext
) extends ActionRefiner[R, R] {

  override protected def refine[A](request: R[A]): Future[Either[Result, R[A]]] =
    request.body match {
      case body: NodeSeq =>
        if (body.nonEmpty) {
          xmlValidationService.validate(body, DepartureDeclarationXSD) match {
            case Right(_) =>
              Future.successful(Right(request))
            case Left(error: XmlError) =>
              Future.successful(Left(BadRequest(error.reason)))
          }
        } else {
          Future.successful(Left(BadRequest(XmlError.RequestBodyEmptyMessage)))
        }
      case _ =>
        Future.successful(Left(BadRequest(XmlError.RequestBodyInvalidTypeMessage)))
    }
}
