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

package routing

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.Inject
import controllers.V1DeparturesController
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import v2.controllers.V2DeparturesController
import v2.controllers.stream.StreamingParsers

class DeparturesRouter @Inject() (val controllerComponents: ControllerComponents, v1Departures: V1DeparturesController, v2Departures: V2DeparturesController)(
  implicit val materializer: Materializer
) extends BaseController
    with StreamingParsers
    with VersionedRouting {

  def submitDeclaration(): Action[Source[ByteString, _]] = route {
    case Some("application/vnd.hmrc.2.0+json") => v2Departures.submitDeclaration()
    case _                                     => v1Departures.submitDeclaration()
  }

}
