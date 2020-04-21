/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import connectors.{ArrivalConnector, MessageConnector}
import controllers.actions.AuthAction
import javax.inject.Inject
import models.domain.MovementMessage
import models.domain.MovementMessage.format
import models.response.Message
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.{BadRequestException, NotFoundException, Upstream4xxResponse}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import utils.Utils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class MessagesController @Inject()(cc: ControllerComponents,
                                   authAction: AuthAction,
                                   messageConnector: MessageConnector)(implicit ec: ExecutionContext) extends BackendController(cc)
{
  def getArrivalMessage(arrivalId: String, messageId: String): Action[AnyContent] =
  authAction.async {
    implicit request => {
      messageConnector.get(arrivalId, messageId).map { response =>
        response.status match {
          case s if Utils.is2xx(s) => {
            val location = response.header(LOCATION)

            location match {
              case Some(locationValue) => Utils.arrivalId(locationValue) match {
                case Success(id) => {
                  val message = response.json.as[MovementMessage]
                  val responseMessage = Message(s"/movements/arrivals/${Utils.urlEncode(id)}/messages/$messageId", message.date, message.message)
                  Ok(Json.toJson(responseMessage))
                }
                case Failure(_) => InternalServerError
              }
              case _ => InternalServerError
            }
          }
        }
      } recover {
        case e: Upstream4xxResponse =>
          if (e.upstreamResponseCode == 400)
            BadRequest
          else if (e.upstreamResponseCode == 401)
            Unauthorized
          else if (e.upstreamResponseCode == 404)
            NotFound
          else if (e.upstreamResponseCode == 423)
            Locked
          else
            InternalServerError
        case _: Throwable =>
          InternalServerError
      }
    }
  }
}
