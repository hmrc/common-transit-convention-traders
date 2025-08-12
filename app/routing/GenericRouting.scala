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
import controllers.MovementsController
import controllers.common.stream.StreamingParsers
import models.VersionedHeader
import models.VersionedJsonHeader
import models.VersionedXmlHeader
import models.common.*
import models.common.errors.PresentationError
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.*
import models.Bindings.*

import java.time.OffsetDateTime
import scala.concurrent.Future

class GenericRouting @Inject() (
  val controllerComponents: ControllerComponents,
  movementsController: MovementsController
)(implicit
  val materializer: Materializer
) extends BaseController
    with StreamingParsers
    with VersionedRouting
    with Logging {

  def attachMessage(movementType: MovementType, id: String): Action[Source[ByteString, ?]] = route {
    _ => runIfBound[MovementId](movementType.movementTypeId, id, movementsController.attachMessage(movementType, _))
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
    _ =>
      movementsController
        .getMovements(movementType, updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil, localReferenceNumber)
  }

  def createMovement(movementType: MovementType): Action[Source[ByteString, ?]] =
    route {
      _ => movementsController.createMovement(movementType)
    }

  def getMovement(movementType: MovementType, id: String): Action[Source[ByteString, ?]] =
    route {
      _ => runIfBound[MovementId](movementType.movementTypeId, id, movementsController.getMovement(movementType, _))
    }

  def getMessageIds(
    movementType: MovementType,
    id: String,
    receivedSince: Option[OffsetDateTime] = None,
    page: Option[PageNumber] = None,
    count: Option[ItemCount] = None,
    receivedUntil: Option[OffsetDateTime] = None
  ): Action[Source[ByteString, ?]] = route {
    _ =>
      runIfBound[MovementId](
        movementType.movementTypeId,
        id,
        movementsController.getMessageIds(movementType, _, receivedSince, page, count, receivedUntil)
      )
  }

  def getMessage(movementType: MovementType, movementId: String, messageId: String): Action[Source[ByteString, ?]] = route {
    _ =>
      runIfBound[MovementId](
        movementType.movementTypeId,
        movementId,
        boundArrivalId =>
          runIfBound[MessageId](
            "messageId",
            messageId,
            movementsController.getMessage(movementType, boundArrivalId, _)
          )
      )
  }

  def getMessageBody(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent] =
    Action.async {
      implicit request =>
        val maybeHeader = request.headers
          .get(HeaderNames.ACCEPT)
          .map(VersionedRouting.validateAcceptHeader)
          .flatMap(_.toOption)

        maybeHeader match {
          case Some(VersionedXmlHeader(_)) | Some(VersionedJsonHeader(_)) => movementsController.getMessageBody(movementType, movementId, messageId)(request)
          case _ => Future.successful(NotAcceptable(Json.toJson(PresentationError.notAcceptableError("The Accept header is missing or invalid."))))
        }
    }
}
