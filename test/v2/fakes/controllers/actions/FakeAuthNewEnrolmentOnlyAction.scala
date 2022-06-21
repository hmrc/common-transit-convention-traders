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

package v2.fakes.controllers.actions

import play.api.mvc.AnyContent
import play.api.mvc.BodyParser
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.test.Helpers
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.controllers.request.AuthenticatedRequest
import v2.models.EORINumber

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

case class FakeAuthNewEnrolmentOnlyAction(eori: EORINumber = EORINumber("id")) extends AuthNewEnrolmentOnlyAction {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] =
    block(AuthenticatedRequest(eori, request))

  override def parser: BodyParser[AnyContent] = Helpers.stubBodyParser()

  override protected def executionContext: ExecutionContext = global
}
