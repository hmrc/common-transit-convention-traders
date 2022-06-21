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

import play.api.mvc.Request
import play.api.mvc.Result
import v2.controllers.actions.MessageSizeAction
import v2.controllers.actions.providers.MessageSizeActionProvider

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

object FakeMessageSizeActionProvider extends MessageSizeActionProvider {
  override def apply[R[_] <: Request[_]](): FakeMessageSizeAction[R] = FakeMessageSizeAction()
}

case class FakeMessageSizeAction[R[_] <: Request[_]]() extends MessageSizeAction[R] {
  override protected def refine[A](request: R[A]): Future[Either[Result, R[A]]] = Future.successful(Right(request))

  override protected def executionContext: ExecutionContext = global
}
