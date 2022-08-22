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
import models.domain.{DepartureId => V1DepartureId}
import models.domain.{MessageId => V1MessageId}
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import v2.controllers.V2DeparturesController
import v2.controllers.stream.StreamingParsers
import v2.models.Bindings._
import v2.models.{DepartureId => V2DepartureId}
import v2.models.{MessageId => V2MessageId}

import java.time.OffsetDateTime

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
      runIfBound[V2DepartureId](
        "departureId",
        departureId,
        boundDepartureId => runIfBound[V2MessageId]("messageId", messageId, v2Departures.getMessage(boundDepartureId, _))
      )
    case _ =>
      runIfBound[V1DepartureId](
        "departureId",
        departureId,
        boundDepartureId => runIfBound[V1MessageId]("messageId", messageId, v1DepartureMessages.getDepartureMessage(boundDepartureId, _))
      )
  }

  def getMessageIds(departureId: String, receivedSince: Option[OffsetDateTime] = None): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE) =>
      runIfBound[V2DepartureId](
        "departureId",
        departureId,
        v2Departures.getMessageIds(_, receivedSince)
      )
    case _ =>
      runIfBound[V1DepartureId](
        "departureId",
        departureId,
        v1DepartureMessages.getDepartureMessages(_, receivedSince)
      )
  }

  def getDeparture(departureId: String): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE) =>
      runIfBound[V2DepartureId](
        "departureId",
        departureId,
        v2Departures.getDeparture
      )
    case _ =>
      runIfBound[V1DepartureId](
        "departureId",
        departureId,
        v1Departures.getDeparture
      )
  }

  def getDeparturesForEori(updatedSince: Option[OffsetDateTime] = None): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE) => v2Departures.getDeparturesForEori(updatedSince)
    case _                                                    => v1Departures.getDeparturesForEori(updatedSince)
  }

}
