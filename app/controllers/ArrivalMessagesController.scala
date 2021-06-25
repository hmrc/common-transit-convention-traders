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

import com.kenshoo.play.metrics.Metrics
import connectors.ArrivalMessageConnector
import controllers.actions.AuthAction
import controllers.actions.ValidateAcceptJsonHeaderAction
import controllers.actions.ValidateArrivalMessageAction
import metrics.HasActionMetrics
import metrics.MetricsKeys
import models.MessageType
import models.response.HateoasArrivalMessagesPostResponseMessage
import models.response.HateoasArrivalResponseMessage
import models.response.HateoasResponseArrivalWithMessages
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.CallOps._
import utils.ResponseHelper
import utils.Utils

import java.time.OffsetDateTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.xml.NodeSeq
import controllers.actions.AnalyseMessageActionProvider

class ArrivalMessagesController @Inject() (
  cc: ControllerComponents,
  authAction: AuthAction,
  messageConnector: ArrivalMessageConnector,
  validateMessageAction: ValidateArrivalMessageAction,
  validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction,
  messageAnalyser: AnalyseMessageActionProvider,
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
      (authAction andThen validateMessageAction andThen messageAnalyser()).async(parse.xml) {
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
                              HateoasArrivalMessagesPostResponseMessage(
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
              Ok(Json.toJson(HateoasArrivalResponseMessage(arrivalId, messageId, m)))
            case Left(response) =>
              handleNon2xx(response)
          }
      }
    }

  def getArrivalMessages(arrivalId: String, receivedSince: Option[OffsetDateTime]): Action[AnyContent] =
    withMetricsTimerAction(GetArrivalMessages) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          messageConnector.getMessages(arrivalId, receivedSince).map {
            case Right(a) =>
              messagesCount.update(a.messages.length)
              Ok(Json.toJson(HateoasResponseArrivalWithMessages(a)))
            case Left(response) =>
              handleNon2xx(response)
          }
      }
    }
}
