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

package v2.fakes.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.BaseController
import play.api.test.Helpers.stubControllerComponents
import v2.controllers.V2MovementsController
import v2.controllers.stream.StreamingParsers
import v2.fakes.utils.FakePreMaterialisedFutureProvider
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType

import java.time.OffsetDateTime

class FakeV2MovementsController @Inject() ()(implicit val materializer: Materializer) extends BaseController with V2MovementsController with StreamingParsers {

  override val controllerComponents          = stubControllerComponents()
  override val preMaterialisedFutureProvider = FakePreMaterialisedFutureProvider

  override def createMovement(movementType: MovementType): Action[Source[ByteString, _]] = Action(streamFromMemory) {
    request =>
      request.body.runWith(Sink.ignore)
      Accepted(Json.obj("version" -> 2))
  }

  override def getMessage(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent] = Action {
    _ =>
      Ok(Json.obj("version" -> 2))
  }

  override def getMessageIds(movementType: MovementType, movementId: MovementId, receivedSince: Option[OffsetDateTime]): Action[AnyContent] = Action {
    _ =>
      Ok(Json.obj("version" -> 2))
  }

  override def getMovement(movementType: MovementType, movementId: MovementId): Action[AnyContent] = Action {
    _ =>
      Ok(Json.obj("version" -> 2))
  }

  def getMovements(movementType: MovementType, updatedSince: Option[OffsetDateTime], movementEORI: Option[EORINumber]): Action[AnyContent] = Action {
    _ =>
      Ok(Json.obj("version" -> 2))
  }

  override def attachMessage(movementType: MovementType, movementId: MovementId): Action[Source[ByteString, _]] = Action(streamFromMemory) {
    request =>
      request.body.runWith(Sink.ignore)
      Accepted(Json.obj("version" -> 2))
  }

  override def attachLargeMessage(movementId: MovementId): Action[Source[ByteString, _]] = Action(streamFromMemory) {
    request =>
      request.body.runWith(Sink.ignore)
      Ok(Json.obj("version" -> 2))
  }
}
