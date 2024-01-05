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

package controllers

import com.google.inject.ImplementedBy
import com.google.inject.Singleton

import java.time.OffsetDateTime
import com.codahale.metrics.MetricRegistry
import config.AppConfig
import config.Constants.MissingECCEnrolmentMessage
import config.Constants.XMissingECCEnrolment
import connectors.DepartureMessageConnector
import controllers.actions.AnalyseMessageActionProvider
import controllers.actions.AuthAction
import controllers.actions.AuthRequest
import controllers.actions.ValidateAcceptJsonHeaderAction
import controllers.actions.ValidateDepartureMessageAction

import javax.inject.Inject
import metrics.HasActionMetrics
import metrics.MetricsKeys
import models.MessageType
import models.domain.DepartureId
import models.domain.MessageId
import models.response.HateoasDepartureMessagesPostResponseMessage
import models.response.HateoasDepartureResponseMessage
import models.response.HateoasResponseDepartureWithMessages
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import v2.utils.CallOps._
import utils.ResponseHelper
import utils.Utils

import scala.concurrent.ExecutionContext
import scala.xml.NodeSeq

@ImplementedBy(classOf[DepartureMessagesController])
trait V1DepartureMessagesController {

  def getDepartureMessage(departureId: DepartureId, messageId: MessageId): Action[AnyContent]

  def getDepartureMessages(departureId: DepartureId, receivedSince: Option[OffsetDateTime]): Action[AnyContent]

  def sendMessageDownstream(departureId: DepartureId): Action[NodeSeq]

}

@Singleton
class DepartureMessagesController @Inject() (
  cc: ControllerComponents,
  authAction: AuthAction,
  messageConnector: DepartureMessageConnector,
  validateMessageAction: ValidateDepartureMessageAction[AuthRequest],
  validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction[AuthRequest],
  messageAnalyser: AnalyseMessageActionProvider,
  val metrics: MetricRegistry,
  config: AppConfig
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with HasActionMetrics
    with HttpErrorFunctions
    with ResponseHelper
    with V1DepartureMessagesController {

  import MetricsKeys.Endpoints._

  lazy val messagesCount = histo(GetDepartureMessagesCount)

  def sendMessageDownstream(departureId: DepartureId): Action[NodeSeq] =
    withMetricsTimerAction(SendDepartureMessage) {
      (authAction andThen validateMessageAction andThen messageAnalyser()).async(parse.xml) {
        implicit request =>
          messageConnector.post(request.body.toString, departureId).map {
            response =>
              response.status match {
                case s if is2xx(s) =>
                  response.header(LOCATION) match {
                    case Some(locationValue) =>
                      MessageType.getMessageType(request.body) match {
                        case Some(messageType: MessageType) =>
                          val messageId = MessageId(Utils.lastFragment(locationValue).toInt)
                          if (config.phase4EnrolmentHeader && !request.hasNewEnrolment) {
                            Accepted(
                              Json.toJson(HateoasDepartureMessagesPostResponseMessage(departureId, messageId, messageType.code, request.body))
                            ).withHeaders(
                              LOCATION             -> routing.routes.DeparturesRouter.getMessage(departureId.toString, messageId.toString).urlWithContext,
                              XMissingECCEnrolment -> MissingECCEnrolmentMessage
                            )
                          } else {
                            Accepted(
                              Json.toJson(HateoasDepartureMessagesPostResponseMessage(departureId, messageId, messageType.code, request.body))
                            ).withHeaders(LOCATION -> routing.routes.DeparturesRouter.getMessage(departureId.toString, messageId.toString).urlWithContext)
                          }
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

  def getDepartureMessages(departureId: DepartureId, receivedSince: Option[OffsetDateTime]): Action[AnyContent] =
    withMetricsTimerAction(GetDepartureMessages) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          messageConnector.getMessages(departureId, receivedSince).map {
            case Right(d) =>
              messagesCount.update(d.messages.length)
              if (config.phase4EnrolmentHeader && !request.hasNewEnrolment) {
                Ok(Json.toJson(HateoasResponseDepartureWithMessages(d)))
                  .withHeaders(XMissingECCEnrolment -> MissingECCEnrolmentMessage)
              } else {
                Ok(Json.toJson(HateoasResponseDepartureWithMessages(d)))
              }
            case Left(response) =>
              handleNon2xx(response)
          }
      }
    }

  def getDepartureMessage(departureId: DepartureId, messageId: MessageId): Action[AnyContent] =
    withMetricsTimerAction(GetDepartureMessage) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          messageConnector.get(departureId, messageId).map {
            case Right(m) =>
              if (config.phase4EnrolmentHeader && !request.hasNewEnrolment) {
                Ok(Json.toJson(HateoasDepartureResponseMessage(departureId, messageId, m)))
                  .withHeaders(XMissingECCEnrolment -> MissingECCEnrolmentMessage)
              } else {
                Ok(Json.toJson(HateoasDepartureResponseMessage(departureId, messageId, m)))
              }
            case Left(response) =>
              handleNon2xx(response)
          }
      }
    }
}
