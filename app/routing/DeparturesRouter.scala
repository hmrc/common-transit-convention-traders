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

package routing

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.Inject
import controllers.V1DepartureMessagesController
import controllers.V1DeparturesController
import models.domain.{DepartureId => V1DepartureId}
import models.domain.{MessageId => V1MessageId}
import play.api.Logging
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import v2.controllers.V2MovementsController
import v2.controllers.stream.StreamingParsers
import v2.models.Bindings._
import v2.models.EORINumber
import v2.models.ItemCount
import v2.models.MovementReferenceNumber
import v2.models.MovementType
import v2.models.PageNumber
import v2.models.{MessageId => V2MessageId}
import v2.models.{MovementId => V2DepartureId}

import java.time.OffsetDateTime
import scala.annotation.nowarn

// This deprecation seems to be a red herring as it triggers on runIfBound
@nowarn("cat=deprecation&msg=method right in class Either is deprecated \\(since 2.13.0\\):")
class DeparturesRouter @Inject() (
  val controllerComponents: ControllerComponents,
  v1Departures: V1DeparturesController,
  v1DepartureMessages: V1DepartureMessagesController,
  v2Departures: V2MovementsController
)(implicit
  val materializer: Materializer
) extends BaseController
    with StreamingParsers
    with VersionedRouting
    with Logging {

  def submitDeclaration(): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) => v2Departures.createMovement(MovementType.Departure)
    case _                                                        => v1Departures.submitDeclaration()
  }

  def getMessage(departureId: String, messageId: String): Action[Source[ByteString, _]] =
    route {
      case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
        runIfBound[V2DepartureId](
          "departureId",
          departureId,
          boundDepartureId =>
            runIfBound[V2MessageId](
              "messageId",
              messageId,
              v2Departures.getMessage(MovementType.Departure, boundDepartureId, _)
            )
        )
      case _ =>
        runIfBound[V1DepartureId](
          "departureId",
          departureId,
          boundDepartureId => runIfBound[V1MessageId]("messageId", messageId, v1DepartureMessages.getDepartureMessage(boundDepartureId, _))
        )
    }

  def getMessageIds(
    departureId: String,
    receivedSince: Option[OffsetDateTime] = None,
    pageNumber: Option[v2.models.PageNumber],
    itemCount: Option[v2.models.ItemCount],
    receivedUntil: Option[OffsetDateTime]
  ): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      runIfBound[V2DepartureId](
        "departureId",
        departureId,
        v2Departures.getMessageIds(MovementType.Departure, _, receivedSince, pageNumber, itemCount, receivedUntil)
      )
    case _ =>
      runIfBound[V1DepartureId](
        "departureId",
        departureId,
        v1DepartureMessages.getDepartureMessages(_, receivedSince)
      )
  }

  def getDeparture(departureId: String): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      runIfBound[V2DepartureId](
        "departureId",
        departureId,
        v2Departures.getMovement(MovementType.Departure, _)
      )
    case _ =>
      runIfBound[V1DepartureId](
        "departureId",
        departureId,
        v1Departures.getDeparture
      )
  }

  def getDeparturesForEori(
    updatedSince: Option[OffsetDateTime] = None,
    movementEORI: Option[EORINumber] = None,
    movementReferenceNumber: Option[MovementReferenceNumber] = None,
    pageNumber: Option[PageNumber] = None,
    itemCount: Option[ItemCount] = None,
    receivedUntil: Option[OffsetDateTime]
  ): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      v2Departures.getMovements(MovementType.Departure, updatedSince, movementEORI, movementReferenceNumber, pageNumber, itemCount, receivedUntil)
    case _ => v1Departures.getDeparturesForEori(updatedSince)
  }

  def attachMessage(departureId: String): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      runIfBound[V2DepartureId](
        "departureId",
        departureId,
        v2Departures.attachMessage(MovementType.Departure, _)
      )
    case _ =>
      runIfBound[V1DepartureId](
        "departureId",
        departureId,
        v1DepartureMessages.sendMessageDownstream
      )
  }

}
