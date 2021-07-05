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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import javax.inject.Inject
import utils.analysis.MessageAnalyser
import scala.xml.NodeSeq
import play.api.mvc.Request
import play.api.mvc.ActionTransformer
import com.google.inject.ImplementedBy

@ImplementedBy(classOf[AnalyseMessageActionProviderImpl])
trait AnalyseMessageActionProvider {
  def apply[F[_] <: Request[_]](): ActionTransformer[F, F]
}

class AnalyseMessageActionProviderImpl @Inject() (
  analyser: MessageAnalyser,
  ec: ExecutionContext
) extends AnalyseMessageActionProvider {

  override def apply[F[_] <: Request[_]](): ActionTransformer[F, F] =
    new AnalyseMessageAction[F](analyser, ec)
}

class AnalyseMessageAction[F[_] <: Request[_]](analyser: MessageAnalyser, ec: ExecutionContext) extends ActionTransformer[F, F] {

  override protected def transform[A](request: F[A]): Future[F[A]] = {
    request.body match {
      case body: NodeSeq =>
        analyser.trackMessageStats(body)
      case _ =>
    }
    Future.successful(request)
  }

  override protected def executionContext: ExecutionContext = ec

}
