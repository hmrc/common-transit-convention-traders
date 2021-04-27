/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.Inject

import com.kenshoo.play.metrics.Metrics
import connectors.ArrivalMessageConnector
import controllers.actions.AuthAction
import controllers.actions.ValidateAcceptJsonHeaderAction
import controllers.actions.ValidateArrivalMessageAction
import metrics.{HasActionMetrics, MetricsKeys}
import models.MessageType
import models.response.HateaosArrivalMessagesPostResponseMessage
import models.response.HateaosArrivalResponseMessage
import models.response.HateaosResponseArrivalWithMessages
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.CallOps._
import utils.ResponseHelper
import utils.Utils

import scala.concurrent.ExecutionContext
import scala.xml.NodeSeq

class ArrivalMessagesController @Inject() (
  cc: ControllerComponents,
  authAction: AuthAction,
  messageConnector: ArrivalMessageConnector,
  validateMessageAction: ValidateArrivalMessageAction,
  validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction,
  val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with HasActionMetrics
    with HttpErrorFunctions
    with ResponseHelper {

  import MetricsKeys.Endpoints._

  lazy val messagesCount = histo(GetArrivalMessagesCount)

  def sendMessageDownstream(arrivalId: String): Action[NodeSeq] =
    withMetricsTimerAction(SendArrivalMessage) {
      (authAction andThen validateMessageAction).async(parse.xml) {
        implicit request =>
          messageConnector.post(request.body.toString, arrivalId).map {
            response =>
              response.status match {
                case s if is2xx(s) =>
                  response.header(LOCATION) match {
                    case Some(locationValue) =>
                      MessageType.getMessageType(request.body) match {
                        case Some(messageType: MessageType) =>
                          val messageId = Utils.lastFragment(locationValue)
                          Accepted(
                            Json.toJson(
                              HateaosArrivalMessagesPostResponseMessage(
                                arrivalId,
                                messageId,
                                messageType.code,
                                request.body
                              )
                            )
                          ).withHeaders(LOCATION -> routes.ArrivalMessagesController.getArrivalMessage(arrivalId, messageId).urlWithContext)
                        case None =>
                          InternalServerError
                      }
                    case _ =>
                      InternalServerError
                  }
                case _ => handleNon2xx(response)
              }
          }
      }
    }

  def getArrivalMessage(arrivalId: String, messageId: String): Action[AnyContent] =
    withMetricsTimerAction(GetArrivalMessage) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          messageConnector.get(arrivalId, messageId).map {
            case Right(m) =>
              Ok(Json.toJson(HateaosArrivalResponseMessage(arrivalId, messageId, m)))
            case Left(response) =>
              handleNon2xx(response)
          }
      }
    }

  def getArrivalMessages(arrivalId: String): Action[AnyContent] =
    withMetricsTimerAction(GetArrivalMessages) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          messageConnector.getMessages(arrivalId).map {
            case Right(a) =>
              messagesCount.update(a.messages.length)
              Ok(Json.toJson(HateaosResponseArrivalWithMessages(a)))
            case Left(response) =>
              handleNon2xx(response)
          }
      }
    }
}
