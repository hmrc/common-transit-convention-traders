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

package controllers.actions.providers

import com.google.inject.ImplementedBy
import com.google.inject.Inject
import controllers.actions.AcceptHeaderAction
import controllers.actions.AcceptHeaderActionImpl
import models.VersionedHeader
import play.api.mvc.Request

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[AcceptHeaderActionProviderImpl])
trait AcceptHeaderActionProvider {
  def apply[R[_] <: Request[?]](acceptedHeaders: Seq[VersionedHeader]): AcceptHeaderAction[R]
}

class AcceptHeaderActionProviderImpl @Inject() ()(implicit ec: ExecutionContext) extends AcceptHeaderActionProvider {

  def apply[R[_] <: Request[?]](acceptedHeaders: Seq[VersionedHeader]): AcceptHeaderAction[R] =
    new AcceptHeaderActionImpl[R](acceptedHeaders: Seq[VersionedHeader])
}
