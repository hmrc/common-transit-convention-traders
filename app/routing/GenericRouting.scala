/*
 * Copyright 2024 HM Revenue & Customs
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
import models.common.*
import models.common.errors.PresentationError
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.*
import play.mvc.Http.HeaderNames
import v2_1.controllers.V2MovementsController
import v2_1.models.Bindings.*

import java.time.OffsetDateTime
import scala.concurrent.Future

class GenericRouting @Inject() (
  val controllerComponents: ControllerComponents,
  v2Movements: V2MovementsController
)(implicit
  val materializer: Materializer
) extends BaseController
    with StreamingParsers
    with VersionedRouting
    with Logging {

  private lazy val validHeaders: Seq[String] = Seq(
    VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value,
    VERSION_2_1_ACCEPT_HEADER_VALUE_XML.value
  )

  private def checkAcceptHeader(implicit request: Request[?]): Option[VersionedAcceptHeader] = {
    val requestHeaderValue = request.headers.get(HeaderNames.ACCEPT)
    requestHeaderValue.flatMap {
      acceptHeaderValue =>
        VersionedRouting.formatAccept(acceptHeaderValue) match {
          case Right(validatedAcceptHeader) if validHeaders.contains(validatedAcceptHeader.value) => Some(validatedAcceptHeader)
          case _                                                                                  => None
        }
    }
  }

  def attachMessage(movementType: MovementType, id: String): Action[Source[ByteString, ?]] = route {
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
      runIfBound[MovementId](movementType.movementTypeId, id, v2Movements.attachMessage(movementType, _))
    case _ => invalidAcceptHeader()
  }

  def getMovementForEori(
    updatedSince: Option[OffsetDateTime] = None,
    movementEORI: Option[EORINumber] = None,
    movementReferenceNumber: Option[MovementReferenceNumber] = None,
    page: Option[PageNumber] = None,
    count: Option[ItemCount] = None,
    receivedUntil: Option[OffsetDateTime] = None,
    localReferenceNumber: Option[LocalReferenceNumber] = None,
    movementType: MovementType
  ): Action[Source[ByteString, ?]] = route {
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
      v2Movements.getMovements(movementType, updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil, localReferenceNumber)
    case _ => invalidAcceptHeader()
  }

  def createMovement(movementType: MovementType): Action[Source[ByteString, ?]] =
    route {
      case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
        v2Movements.createMovement(movementType)
      case _ => invalidAcceptHeader()
    }

  def getMovement(movementType: MovementType, id: String): Action[Source[ByteString, ?]] =
    route {
      case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
        runIfBound[MovementId](movementType.movementTypeId, id, v2Movements.getMovement(movementType, _))
      case _ => invalidAcceptHeader()
    }

  def getMessageIds(
    movementType: MovementType,
    id: String,
    receivedSince: Option[OffsetDateTime] = None,
    page: Option[PageNumber] = None,
    count: Option[ItemCount] = None,
    receivedUntil: Option[OffsetDateTime] = None
  ): Action[Source[ByteString, ?]] = route {
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
      runIfBound[MovementId](movementType.movementTypeId, id, v2Movements.getMessageIds(MovementType.Arrival, _, receivedSince, page, count, receivedUntil))
    case _ => invalidAcceptHeader()
  }

  def getMessage(movementType: MovementType, movementId: String, messageId: String): Action[Source[ByteString, ?]] = route {
    case Some(VersionedRouting.VERSION_2_1_ACCEPT_HEADER_PATTERN()) =>
      runIfBound[MovementId](
        movementType.movementTypeId,
        movementId,
        boundArrivalId =>
          runIfBound[MessageId](
            "messageId",
            messageId,
            v2Movements.getMessage(MovementType.Arrival, boundArrivalId, _)
          )
      )
    case _ => invalidAcceptHeader()
  }

  def getMessageBody(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent] =
    Action.async {
      implicit request =>
        checkAcceptHeader match {
          case Some(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON) | Some(VERSION_2_1_ACCEPT_HEADER_VALUE_XML) =>
            v2Movements.getMessageBody(movementType, movementId, messageId)(request)
          case _ => Future.successful(NotAcceptable(Json.toJson(PresentationError.notAcceptableError("The Accept header is missing or invalid."))))
        }
    }
}
