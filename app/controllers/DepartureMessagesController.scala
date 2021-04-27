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
import connectors.DepartureMessageConnector
import controllers.actions.AuthAction
import controllers.actions.ValidateAcceptJsonHeaderAction
import controllers.actions.ValidateDepartureMessageAction
import metrics.{HasActionMetrics, MetricsKeys}
import models.MessageType
import models.response.HateaosDepartureMessagesPostResponseMessage
import models.response.HateaosDepartureResponseMessage
import models.response.HateaosResponseDepartureWithMessages
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

class DepartureMessagesController @Inject() (
  cc: ControllerComponents,
  authAction: AuthAction,
  messageConnector: DepartureMessageConnector,
  validateMessageAction: ValidateDepartureMessageAction,
  validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction,
  val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with HasActionMetrics
    with HttpErrorFunctions
    with ResponseHelper {

  import MetricsKeys.Endpoints._

  lazy val messagesCount = histo(GetDepartureMessagesCount)

  def sendMessageDownstream(departureId: String): Action[NodeSeq] =
    withMetricsTimerAction(SendDepartureMessage) {
      (authAction andThen validateMessageAction).async(parse.xml) {
        implicit request =>
          messageConnector.post(request.body.toString, departureId).map {
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
                              HateaosDepartureMessagesPostResponseMessage(
                                departureId,
                                messageId,
                                messageType.code,
                                request.body
                              )
                            )
                          ).withHeaders(LOCATION -> routes.DepartureMessagesController.getDepartureMessage(departureId, messageId).urlWithContext)
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

  def getDepartureMessages(departureId: String): Action[AnyContent] =
    withMetricsTimerAction(GetDepartureMessages) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          messageConnector.getMessages(departureId).map {
            case Right(d) =>
              messagesCount.update(d.messages.length)
              Ok(Json.toJson(HateaosResponseDepartureWithMessages(d)))
            case Left(response) =>
              handleNon2xx(response)
          }
      }
    }

  def getDepartureMessage(departureId: String, messageId: String): Action[AnyContent] =
    withMetricsTimerAction(GetDepartureMessage) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          messageConnector.get(departureId, messageId).map {
            case Right(m) =>
              Ok(Json.toJson(HateaosDepartureResponseMessage(departureId, messageId, m)))
            case Left(response) =>
              handleNon2xx(response)
          }
      }
    }
}
