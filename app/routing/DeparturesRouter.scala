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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.google.inject.Inject
import config.AppConfig
import controllers.V1DepartureMessagesController
import controllers.V1DeparturesController
import controllers.common.stream.StreamingParsers
import models.common.EORINumber
import models.common.ItemCount
import models.common.LocalReferenceNumber
import models.common.MovementReferenceNumber
import models.common.MovementType
import models.common.PageNumber
import models.common.errors.PresentationError
import models.common.{MessageId => V2MessageId}
import models.common.{MovementId => V2DepartureId}
import models.domain.{DepartureId => V1DepartureId}
import models.domain.{MessageId => V1MessageId}
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import v2.controllers.{V2MovementsController => V2TransitionalMovementsController}
import v2_1.controllers.V2MovementsController
import v2.models.Bindings._

import java.time.OffsetDateTime

class DeparturesRouter @Inject() (
  val controllerComponents: ControllerComponents,
  v1Departures: V1DeparturesController,
  v1DepartureMessages: V1DepartureMessagesController,
  v2TransitionalDepartures: V2TransitionalMovementsController,
  v2Departures: V2MovementsController,
  config: AppConfig
)(implicit
  val materializer: Materializer
) extends BaseController
    with StreamingParsers
    with VersionedRouting
    with Logging {

  def submitDeclaration(): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      if (config.enablePhase5) v2TransitionalDepartures.createMovement(MovementType.Departure)
      else handleEnablingPhase5()
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
      v2Departures.createMovement(MovementType.Departure)
    case _ =>
      if (config.disablePhase4) handleDisablingPhase4()
      else v1Departures.submitDeclaration()
  }

  private def handleDisablingPhase4() =
    Action(streamFromMemory) {
      request =>
        request.body.runWith(Sink.ignore)
        val presentationError: PresentationError = PresentationError.goneError(
          "New NCTS4 Departure Declarations can no longer be created using CTC Traders API v1.0. Use CTC Traders API v2.0 to create new NCTS5 Departure Declarations."
        )
        Status(presentationError.code.statusCode)(Json.toJson(presentationError))
    }

  def getMessage(departureId: String, messageId: String): Action[Source[ByteString, _]] =
    route {
      case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
        if (config.enablePhase5)
          runIfBound[V2DepartureId](
            "departureId",
            departureId,
            boundDepartureId =>
              runIfBound[V2MessageId](
                "messageId",
                messageId,
                v2TransitionalDepartures.getMessage(MovementType.Departure, boundDepartureId, _)
              )
          )
        else handleEnablingPhase5()
      case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
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
    page: Option[PageNumber],
    count: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime]
  ): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      if (config.enablePhase5)
        runIfBound[V2DepartureId](
          "departureId",
          departureId,
          v2TransitionalDepartures.getMessageIds(MovementType.Departure, _, receivedSince, page, count, receivedUntil)
        )
      else handleEnablingPhase5()
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
      runIfBound[V2DepartureId]("departureId", departureId, v2Departures.getMessageIds(MovementType.Departure, _, receivedSince, page, count, receivedUntil))
    case _ =>
      runIfBound[V1DepartureId](
        "departureId",
        departureId,
        v1DepartureMessages.getDepartureMessages(_, receivedSince)
      )
  }

  def getDeparture(departureId: String): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      if (config.enablePhase5)
        runIfBound[V2DepartureId](
          "departureId",
          departureId,
          v2TransitionalDepartures.getMovement(MovementType.Departure, _)
        )
      else handleEnablingPhase5()
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
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
    page: Option[PageNumber] = None,
    count: Option[ItemCount] = None,
    receivedUntil: Option[OffsetDateTime] = None,
    localReferenceNumber: Option[LocalReferenceNumber] = None
  ): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      if (config.enablePhase5)
        v2TransitionalDepartures.getMovements(
          MovementType.Departure,
          updatedSince,
          movementEORI,
          movementReferenceNumber,
          page,
          count,
          receivedUntil,
          localReferenceNumber
        )
      else handleEnablingPhase5()
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
      v2Departures.getMovements(MovementType.Departure, updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil, localReferenceNumber)
    case _ => v1Departures.getDeparturesForEori(updatedSince)
  }

  def attachMessage(departureId: String): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      if (config.enablePhase5) runIfBound[V2DepartureId]("departureId", departureId, v2TransitionalDepartures.attachMessage(MovementType.Departure, _))
      else handleEnablingPhase5()
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
      runIfBound[V2DepartureId]("departureId", departureId, v2Departures.attachMessage(MovementType.Departure, _))
    case _ =>
      runIfBound[V1DepartureId](
        "departureId",
        departureId,
        v1DepartureMessages.sendMessageDownstream
      )
  }

}
