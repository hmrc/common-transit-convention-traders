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

import com.google.inject.Inject
import config.AppConfig
import controllers.actions.AuthRequest
import play.api.mvc.BodyParsers
import play.api.mvc.Request
import play.api.mvc.Result
import uk.gov.hmrc.auth.core.AuthConnector
import v2.controllers.actions.AuthNewEnrolmentOnlyAction

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class FakeAuthNewEnrolmentOnlyAction @Inject() (
  override val authConnector: AuthConnector,
  config: AppConfig,
  override val parser: BodyParsers.Default
)(implicit override val executionContext: ExecutionContext)
    extends AuthNewEnrolmentOnlyAction(authConnector, parser) {

  override def invokeBlock[A](request: Request[A], block: AuthRequest[A] => Future[Result]): Future[Result] =
    block(AuthRequest(request, "id"))
}
