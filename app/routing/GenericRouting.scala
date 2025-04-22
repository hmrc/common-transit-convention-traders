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
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import models.common.errors.PresentationError
import org.apache.pekko.stream.Materializer
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.*
import play.mvc.Http.HeaderNames
import v2_1.controllers.V2MovementsController

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
