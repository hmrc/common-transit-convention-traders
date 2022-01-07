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
import models.request.GuaranteedRequest
import play.api.mvc.Results.BadRequest
import play.api.mvc.ActionRefiner
import play.api.mvc.Request
import play.api.mvc.Result
import services.EnsureGuaranteeService

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.xml.NodeSeq

class EnsureGuaranteeAction @Inject() (ensureGuaranteeService: EnsureGuaranteeService)(implicit val executionContext: ExecutionContext)
    extends ActionRefiner[Request, GuaranteedRequest] {

  override protected def refine[A](request: Request[A]): Future[Either[Result, GuaranteedRequest[A]]] =
    request.body match {
      case body: NodeSeq if body.nonEmpty =>
        ensureGuaranteeService.ensureGuarantee(body) match {
          case Left(error) =>
            Future.successful(Left(BadRequest(error.message)))
          case Right(newBody) =>
            Future.successful(Right(GuaranteedRequest[A](request, newBody, guaranteeAdded = newBody != body)))
        }
      case _ =>
        Future.successful(Left(BadRequest))
    }
}
