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
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.Inject
import config.AppConfig
import controllers.V1ArrivalMessagesController
import controllers.V1ArrivalMovementController
import play.api.mvc.Action
import v2.models.Bindings._
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import v2.controllers.V2MovementsController
import v2.controllers.stream.StreamingParsers
import models.domain.{MessageId => V1MessageId}
import v2.models.EORINumber
import v2.models.ItemCount
import v2.models.LocalReferenceNumber
import v2.models.MovementReferenceNumber
import v2.models.MovementType
import v2.models.PageNumber
import v2.models.{MessageId => V2MessageId}
import v2.models.{MovementId => V2ArrivalId}
import models.domain.{ArrivalId => V1ArrivalId}
import play.api.Logging
import play.api.libs.json.Json
import v2.models.errors.PresentationError

import java.time.OffsetDateTime
import scala.annotation.nowarn

// This deprecation seems to be a red herring as it triggers on runIfBound
@nowarn("cat=deprecation&msg=method right in class Either is deprecated \\(since 2.13.0\\):")
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
      case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) => v2Arrivals.createMovement(MovementType.Arrival)
      case _ =>
        if (config.disablePhase4) handleDisablingPhase4()
        else v1Arrivals.createArrivalNotification()
    }

  private def handleDisablingPhase4() =
    Action(streamFromMemory) {
      request =>
        request.body.runWith(Sink.ignore)
        val presentationError = PresentationError.goneError("Please use CTC Traders API v2.0 to create an Arrival Notification")
        Status(presentationError.code.statusCode)(Json.toJson(presentationError))
    }

  def getArrival(arrivalId: String): Action[Source[ByteString, _]] =
    route {
      case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
        runIfBound[V2ArrivalId](
          "arrivalId",
          arrivalId,
          v2Arrivals.getMovement(MovementType.Arrival, _)
        )
      case _ =>
        runIfBound[V1ArrivalId](
          "arrivalId",
          arrivalId,
          v1Arrivals.getArrival(_)
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
      runIfBound[V2ArrivalId](
        "arrivalId",
        arrivalId,
        v2Arrivals.getMessageIds(MovementType.Arrival, _, receivedSince, page, count, receivedUntil)
      )
    case _ =>
      runIfBound[V1ArrivalId](
        "arrivalId",
        arrivalId,
        v1ArrivalMessages.getArrivalMessages(_, receivedSince)
      )
  }

  def getArrivalMessage(arrivalId: String, messageId: String): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
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
      v2Arrivals.getMovements(MovementType.Arrival, updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil, localReferenceNumber)
    case _ => v1Arrivals.getArrivalsForEori(updatedSince)
  }

  def attachMessage(arrivalId: String): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) =>
      runIfBound[V2ArrivalId](
        "arrivalId",
        arrivalId,
        v2Arrivals.attachMessage(MovementType.Arrival, _)
      )
    case _ =>
      runIfBound[V1ArrivalId](
        "arrivalId",
        arrivalId,
        v1ArrivalMessages.sendMessageDownstream
      )
  }

}
