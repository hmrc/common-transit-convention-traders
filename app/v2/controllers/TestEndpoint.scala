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

package v2.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.Inject
import play.api.Logging
import play.api.libs.streams.Accumulator
import play.api.mvc.Action
import play.api.mvc.BodyParser
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import v2.controllers.stream.StreamingParsers

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class TestEndpoint @Inject() (controllerComponents: ControllerComponents)(implicit ec: ExecutionContext, val materializer: Materializer)
    extends BackendController(controllerComponents)
    with StreamingParsers
    with Logging {

  def echo(): Action[Source[ByteString, _]] = Action.async(
    BodyParser(
      _ => Accumulator.source[ByteString].map(Right.apply)
    )
  ) {
    request => Future.successful(Ok.chunked(request.body))
  }

}
