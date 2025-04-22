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

import com.google.inject.Inject
import controllers.common.stream.StreamingParsers
import models.common.EORINumber
import models.common.ItemCount
import models.common.LocalReferenceNumber
import models.common.MovementReferenceNumber
import models.common.MovementType
import models.common.PageNumber
import models.common.MessageId as V2MessageId
import models.common.MovementId as V2ArrivalId
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import v2_1.models.Bindings.*
import v2_1.controllers.V2MovementsController

import java.time.OffsetDateTime

class ArrivalsRouter @Inject() (
  val controllerComponents: ControllerComponents,
  v2Arrivals: V2MovementsController
)(implicit
  val materializer: Materializer
) extends BaseController
    with StreamingParsers
    with VersionedRouting
    with Logging {

  def createArrivalNotification(): Action[Source[ByteString, ?]] =
    route {
      case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
        v2Arrivals.createMovement(MovementType.Arrival)
      case _ => invalidAcceptHeader()
    }

  def getArrival(arrivalId: String): Action[Source[ByteString, ?]] =
    route {
      case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
        runIfBound[V2ArrivalId]("arrivalId", arrivalId, v2Arrivals.getMovement(MovementType.Arrival, _))
      case _ => invalidAcceptHeader()
    }

  def getArrivalMessageIds(
    arrivalId: String,
    receivedSince: Option[OffsetDateTime] = None,
    page: Option[PageNumber] = None,
    count: Option[ItemCount] = None,
    receivedUntil: Option[OffsetDateTime] = None
  ): Action[Source[ByteString, ?]] = route {
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
      runIfBound[V2ArrivalId]("arrivalId", arrivalId, v2Arrivals.getMessageIds(MovementType.Arrival, _, receivedSince, page, count, receivedUntil))
    case _ => invalidAcceptHeader()
  }

  def getArrivalMessage(arrivalId: String, messageId: String): Action[Source[ByteString, ?]] = route {
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
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
    case _ => invalidAcceptHeader()
  }

  def getArrivalsForEori(
    updatedSince: Option[OffsetDateTime] = None,
    movementEORI: Option[EORINumber] = None,
    movementReferenceNumber: Option[MovementReferenceNumber] = None,
    page: Option[PageNumber] = None,
    count: Option[ItemCount] = None,
    receivedUntil: Option[OffsetDateTime] = None,
    localReferenceNumber: Option[LocalReferenceNumber] = None
  ): Action[Source[ByteString, ?]] = route {
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
      v2Arrivals.getMovements(MovementType.Arrival, updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil, localReferenceNumber)
    case _ => invalidAcceptHeader()
  }

  def attachMessage(arrivalId: String): Action[Source[ByteString, ?]] = route {
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
      runIfBound[V2ArrivalId]("arrivalId", arrivalId, v2Arrivals.attachMessage(MovementType.Arrival, _))
    case _ => invalidAcceptHeader()
  }
}
