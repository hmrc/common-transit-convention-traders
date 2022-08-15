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
import controllers.V1DepartureMessagesController
import controllers.V1DeparturesController
//import controllers.V1DepartureController
import models.domain.{DepartureId => V1DepartureId}
import models.domain.{MessageId => V1MessageId}
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import play.api.mvc.PathBindable
import v2.controllers.V2DeparturesController
import v2.controllers.stream.StreamingParsers
import v2.models.Bindings._
import v2.models.{DepartureId => V2DepartureId}
import v2.models.{MessageId => V2MessageId}

class DeparturesRouter @Inject() (
  val controllerComponents: ControllerComponents,
  v1Departures: V1DeparturesController,
  v1DepartureMessages: V1DepartureMessagesController,
  v2Departures: V2DeparturesController
)(implicit
  val materializer: Materializer
) extends BaseController
    with StreamingParsers
    with VersionedRouting {

  def submitDeclaration(): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE) => v2Departures.submitDeclaration()
    case _                                                    => v1Departures.submitDeclaration()
  }

  def getMessage(departureId: String, messageId: String): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE) =>
      (for {
        convertedDepartureId <- implicitly[PathBindable[V2DepartureId]].bind("departureId", departureId)
        convertedMessageId   <- implicitly[PathBindable[V2MessageId]].bind("messageId", messageId)
      } yield (convertedDepartureId, convertedMessageId)).fold(
        bindingFailureAction(_),
        converted => v2Departures.getMessage(converted._1, converted._2)
      )
    case _ =>
      (for {
        convertedDepartureId <- implicitly[PathBindable[V1DepartureId]].bind("departureId", departureId)
        convertedMessageId   <- implicitly[PathBindable[V1MessageId]].bind("messageId", messageId)
      } yield (convertedDepartureId, convertedMessageId)).fold(
        bindingFailureAction(_),
        converted => v1DepartureMessages.getDepartureMessage(converted._1, converted._2)
      )

  }

  def getDeparture(departureId: String): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE) =>
      (for {
        convertedDepartureId <- implicitly[PathBindable[V2DepartureId]].bind("departureId", departureId)
      } yield convertedDepartureId).fold(
        bindingFailureAction(_),
        converted => v2Departures.getDeparture(converted)
      )

    case _ =>
      (for {
        convertedDepartureId <- implicitly[PathBindable[V1DepartureId]].bind("departureId", departureId)
      } yield convertedDepartureId).fold(
        bindingFailureAction(_),
        converted => v1Departures.getDeparture(converted)
      )
  }
}
