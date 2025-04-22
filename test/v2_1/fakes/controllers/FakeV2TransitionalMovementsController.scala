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

package v2_1.fakes.controllers

import com.google.inject.Inject
import controllers.common.stream.StreamingParsers
import models.common._
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.stubControllerComponents
import v2_1.controllers.V2MovementsController
import v2_1.models.responses.UpscanResponse

import java.time.OffsetDateTime

class FakeV2TransitionalMovementsController @Inject() ()(implicit val materializer: Materializer)
    extends BaseController
    with V2MovementsController
    with StreamingParsers
    with Logging {

  override val controllerComponents: ControllerComponents = stubControllerComponents()

  override def createMovement(movementType: MovementType): Action[Source[ByteString, ?]] = Action(streamFromMemory) {
    request =>
      request.body.runWith(Sink.ignore)
      Accepted(Json.obj("version" -> 2))
  }

  override def getMessage(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent] = Action {
    _ =>
      Ok(Json.obj("version" -> 2))
  }

  override def getMessageIds(
    movementType: MovementType,
    movementId: MovementId,
    receivedSince: Option[OffsetDateTime],
    pageNumber: Option[PageNumber],
    itemCount: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime]
  ): Action[AnyContent] = Action {
    _ =>
      Ok(Json.obj("version" -> 2))
  }

  override def getMovement(movementType: MovementType, movementId: MovementId): Action[AnyContent] = Action {
    _ =>
      Ok(Json.obj("version" -> 2))
  }

  def getMovements(
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber],
    pageNumber: Option[PageNumber],
    itemCount: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime],
    localReferenceNumber: Option[LocalReferenceNumber]
  ): Action[AnyContent] = Action {
    _ =>
      Ok(Json.obj("version" -> 2))
  }

  override def attachMessage(movementType: MovementType, movementId: MovementId): Action[Source[ByteString, ?]] = Action(streamFromMemory) {
    request =>
      request.body.runWith(Sink.ignore)
      Accepted(Json.obj("version" -> 2))
  }

  override def attachMessageFromUpscan(
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    clientId: Option[ClientId]
  ): Action[UpscanResponse] =
    Action(parse.json[UpscanResponse]) {
      _ =>
        Ok(Json.obj("version" -> 2))
    }

  override def getMessageBody(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent] = Action {
    _ =>
      Ok(Json.obj("version" -> 2))
  }

}
