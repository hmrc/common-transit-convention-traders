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

package v2.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.Logging
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import routing.VersionedRouting
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.controllers.actions.MessageSizeAction
import v2.controllers.stream.StreamingParsers

import scala.concurrent.Future

@ImplementedBy(classOf[V2DeparturesControllerImpl])
trait V2DeparturesController {

  def submitDeclaration(): Action[Source[ByteString, _]]

}

@Singleton
class V2DeparturesControllerImpl @Inject() (
    val controllerComponents: ControllerComponents,
    authActionNewEnrolmentOnly: AuthNewEnrolmentOnlyAction,
    messageSizeAction: MessageSizeAction)(implicit val materializer: Materializer) extends BaseController
  with V2DeparturesController
  with Logging
  with StreamingParsers
  with VersionedRouting {

  def submitDeclaration(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction).async(streamFromMemory) {
      request =>
        // TODO: When streaming to the validation service, we will want to keep a copy of the
        //  stream so we can replay it. We will need to do one of two things:
        //  * Stream to a temporary file, then stream off it twice
        //  * Stream from memory to the validation service AND a file, then stream FROM the file if we get the OK
        //  The first option will likely be more stable but slower than the second option.

        // Because we have an open stream, we **must** do something with it. For now, we send it to the ignore sink.
        request.body.to(Sink.ignore).run()
        logger.info("Version 2 of endpoint has been called")
        Future.successful(Accepted)
    }

}
