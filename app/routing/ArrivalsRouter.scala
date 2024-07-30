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
import controllers.V1ArrivalMessagesController
import controllers.V1ArrivalMovementController
import controllers.common.stream.StreamingParsers
import models.common.EORINumber
import models.common.ItemCount
import models.common.LocalReferenceNumber
import models.common.MovementReferenceNumber
import models.common.MovementType
import models.common.PageNumber
import models.common.errors.PresentationError
import models.common.{MessageId => V2MessageId}
import models.common.{MovementId => V2ArrivalId}
import play.api.mvc.Action
import v2.models.Bindings._
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import v2.controllers.V2MovementsController
import models.domain.{MessageId => V1MessageId}
import models.domain.{ArrivalId => V1ArrivalId}
import play.api.Logging
import play.api.libs.json.Json

import java.time.OffsetDateTime

class ArrivalsRouter @Inject() (
  val controllerComponents: ControllerComponents,
  v1Arrivals: V1ArrivalMovementController,
  v2Arrivals: V2MovementsController,
  v1ArrivalMessages: V1ArrivalMessagesController,
  config: AppConfig
)(implicit
  val materializer: Materializer
) extends BaseController
    with StreamingParsers
    with VersionedRouting
    with Logging {

  def createArrivalNotification(): Action[Source[ByteString, _]] =
    route {
      case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
        if (config.enablePhase5) v2Arrivals.createMovement(MovementType.Arrival)
        else handleEnablingPhase5()
      case _ =>
        if (config.disablePhase4) handleDisablingPhase4()
        else v1Arrivals.createArrivalNotification()
    }

  private def handleDisablingPhase4() =
    Action(streamFromMemory) {
      request =>
        request.body.runWith(Sink.ignore)
        val presentationError: PresentationError = PresentationError.goneError(
          "New NCTS4 Arrival Notifications can no longer be created using CTC Traders API v1.0. Use CTC Traders API v2.0 to create new NCTS5 Arrival Notifications."
        )
        Status(presentationError.code.statusCode)(Json.toJson(presentationError))
    }

  def getArrival(arrivalId: String): Action[Source[ByteString, _]] =
    route {
      case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
        if (config.enablePhase5)
          runIfBound[V2ArrivalId]("arrivalId", arrivalId, v2Arrivals.getMovement(MovementType.Arrival, _))
        else handleEnablingPhase5()
      case _ =>
        runIfBound[V1ArrivalId](
          "arrivalId",
          arrivalId,
          v1Arrivals.getArrival
        )
    }

  def getArrivalMessageIds(
    arrivalId: String,
    receivedSince: Option[OffsetDateTime] = None,
    page: Option[PageNumber] = None,
    count: Option[ItemCount] = None,
    receivedUntil: Option[OffsetDateTime] = None
  ): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      if (config.enablePhase5)
        runIfBound[V2ArrivalId]("arrivalId", arrivalId, v2Arrivals.getMessageIds(MovementType.Arrival, _, receivedSince, page, count, receivedUntil))
      else handleEnablingPhase5()
    case _ =>
      runIfBound[V1ArrivalId](
        "arrivalId",
        arrivalId,
        v1ArrivalMessages.getArrivalMessages(_, receivedSince)
      )
  }

  def getArrivalMessage(arrivalId: String, messageId: String): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      if (config.enablePhase5)
        runIfBound[V2ArrivalId](
          "arrivalId",
          arrivalId,
          boundArrivalId =>
            runIfBound[V2MessageId](
              "messageId",
              messageId,
              v2Arrivals.getMessage(MovementType.Arrival, boundArrivalId, _)
            )
        )
      else handleEnablingPhase5()
    case _ =>
      runIfBound[V1ArrivalId](
        "arrivalId",
        arrivalId,
        boundArrivalId => runIfBound[V1MessageId]("messageId", messageId, v1ArrivalMessages.getArrivalMessage(boundArrivalId, _))
      )
  }

  def getArrivalsForEori(
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
        v2Arrivals.getMovements(MovementType.Arrival, updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil, localReferenceNumber)
      else handleEnablingPhase5()
    case _ => v1Arrivals.getArrivalsForEori(updatedSince)
  }

  def attachMessage(arrivalId: String): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      if (config.enablePhase5) runIfBound[V2ArrivalId]("arrivalId", arrivalId, v2Arrivals.attachMessage(MovementType.Arrival, _))
      else handleEnablingPhase5()
    case _ =>
      runIfBound[V1ArrivalId](
        "arrivalId",
        arrivalId,
        v1ArrivalMessages.sendMessageDownstream
      )
  }

}
