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

package v2.controllers.request

import play.api.mvc.Request
import play.api.mvc.WrappedRequest
import v2.models.EORINumber

abstract class BodyReplaceableRequest[+R[_], B](request: Request[B]) extends WrappedRequest[B](request) {
  def replaceBody(body: B): R[B]
}

case class AuthenticatedRequest[A](eoriNumber: EORINumber, request: Request[A]) extends BodyReplaceableRequest[AuthenticatedRequest, A](request) {
  override def replaceBody(body: A): AuthenticatedRequest[A] = AuthenticatedRequest(eoriNumber, request.withBody(body))
}
