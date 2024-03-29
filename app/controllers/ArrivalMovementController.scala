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
import com.codahale.metrics.MetricRegistry
import config.AppConfig
import config.Constants.MissingECCEnrolmentMessage
import config.Constants.XMissingECCEnrolment
import connectors.ArrivalConnector
import controllers.actions.AnalyseMessageActionProvider
import controllers.actions.AuthAction
import controllers.actions.AuthRequest
import controllers.actions.ValidateAcceptJsonHeaderAction
import controllers.actions.ValidateArrivalNotificationAction
import metrics.HasActionMetrics
import metrics.MetricsKeys
import models.MessageType
import models.domain.ArrivalId
import models.domain.Arrivals
import models.response.HateoasArrivalMovementPostResponseMessage
import models.response.HateoasResponseArrival
import models.response.HateoasResponseArrivals
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import v2.utils.CallOps._
import utils.ResponseHelper
import utils.Utils

import java.time.OffsetDateTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.xml.NodeSeq

@ImplementedBy(classOf[ArrivalMovementController])
trait V1ArrivalMovementController {
  def createArrivalNotification(): Action[NodeSeq]
  def getArrival(arrivalId: ArrivalId): Action[AnyContent]
  def getArrivalsForEori(updatedSince: Option[OffsetDateTime]): Action[AnyContent]
}

@Singleton
class ArrivalMovementController @Inject() (
  cc: ControllerComponents,
  authAction: AuthAction,
  arrivalConnector: ArrivalConnector,
  validateArrivalNotificationAction: ValidateArrivalNotificationAction[AuthRequest],
  validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction[AuthRequest],
  messageAnalyser: AnalyseMessageActionProvider,
  val metrics: MetricRegistry,
  config: AppConfig
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with HasActionMetrics
    with HttpErrorFunctions
    with ResponseHelper
    with V1ArrivalMovementController {

  import MetricsKeys.Endpoints._

  lazy val arrivalsCount = histo(GetArrivalsForEoriCount)

  def createArrivalNotification(): Action[NodeSeq] =
    withMetricsTimerAction(CreateArrivalNotification) {
      (authAction andThen validateArrivalNotificationAction andThen messageAnalyser()).async(parse.xml) {
        implicit request =>
          arrivalConnector.post(request.body.toString).map {
            case Right(response) =>
              response.header(LOCATION) match {
                case Some(locationValue: String) =>
                  MessageType.getMessageType(request.body) match {
                    case Some(messageType: MessageType) =>
                      val arrivalId = ArrivalId(Utils.lastFragment(locationValue).toInt)
                      if (config.phase4EnrolmentHeader && !request.hasNewEnrolment) {
                        Accepted(
                          Json.toJson(HateoasArrivalMovementPostResponseMessage(arrivalId, messageType.code, request.body, response.responseData))
                        ).withHeaders(
                          LOCATION             -> routing.routes.ArrivalsRouter.getArrival(arrivalId.toString).urlWithContext,
                          XMissingECCEnrolment -> MissingECCEnrolmentMessage
                        )
                      } else {
                        Accepted(
                          Json.toJson(HateoasArrivalMovementPostResponseMessage(arrivalId, messageType.code, request.body, response.responseData))
                        ).withHeaders(LOCATION -> routing.routes.ArrivalsRouter.getArrival(arrivalId.toString).urlWithContext)
                      }
                    case None =>
                      logger.error("No valid matching message type found for Arrival")
                      InternalServerError
                  }
                case _ =>
                  logger.error("Location header is not available with the response for Arrival")
                  InternalServerError
              }
            case Left(response) => handleNon2xx(response)
          }
      }
    }

  def resubmitArrivalNotification(arrivalId: ArrivalId): Action[NodeSeq] =
    withMetricsTimerAction(ResubmitArrivalNotification) {
      (authAction andThen validateArrivalNotificationAction andThen messageAnalyser()).async(parse.xml) {
        implicit request =>
          arrivalConnector.put(request.body.toString, arrivalId).map {
            case Right(response) =>
              response.header(LOCATION) match {
                case Some(locationValue: String) =>
                  MessageType.getMessageType(request.body) match {
                    case Some(messageType: MessageType) =>
                      val arrivalId = ArrivalId(Utils.lastFragment(locationValue).toInt)
                      if (config.phase4EnrolmentHeader && !request.hasNewEnrolment) {
                        Accepted(
                          Json.toJson(HateoasArrivalMovementPostResponseMessage(arrivalId, messageType.code, request.body, response.responseData))
                        ).withHeaders(
                          LOCATION             -> routing.routes.ArrivalsRouter.getArrival(arrivalId.toString).urlWithContext,
                          XMissingECCEnrolment -> MissingECCEnrolmentMessage
                        )
                      } else {
                        Accepted(
                          Json.toJson(HateoasArrivalMovementPostResponseMessage(arrivalId, messageType.code, request.body, response.responseData))
                        ).withHeaders(LOCATION -> routing.routes.ArrivalsRouter.getArrival(arrivalId.toString).urlWithContext)
                      }
                    case None =>
                      logger.error("No valid matching message type found for Arrival")
                      InternalServerError
                  }
                case _ =>
                  logger.error("Location header is not available with the response for Arrival")
                  InternalServerError
              }
            case Left(response) =>
              handleNon2xx(response)
          }
      }
    }

  def getArrival(arrivalId: ArrivalId): Action[AnyContent] =
    withMetricsTimerAction(GetArrival) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          arrivalConnector.get(arrivalId).map {
            case Right(arrival) =>
              if (config.phase4EnrolmentHeader && !request.hasNewEnrolment) {
                Ok(Json.toJson(HateoasResponseArrival(arrival)))
                  .withHeaders(XMissingECCEnrolment -> MissingECCEnrolmentMessage)
              } else {
                Ok(Json.toJson(HateoasResponseArrival(arrival)))
              }
            case Left(invalidResponse) =>
              handleNon2xx(invalidResponse)
          }
      }
    }

  def getArrivalsForEori(updatedSince: Option[OffsetDateTime]): Action[AnyContent] =
    withMetricsTimerAction(GetArrivalsForEori) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          arrivalConnector.getForEori(updatedSince).map {
            case Right(arrivals: Arrivals) =>
              arrivalsCount.update(arrivals.arrivals.length)
              if (config.phase4EnrolmentHeader && !request.hasNewEnrolment) {
                Ok(Json.toJson(HateoasResponseArrivals(arrivals)))
                  .withHeaders(XMissingECCEnrolment -> MissingECCEnrolmentMessage)
              } else {
                Ok(Json.toJson(HateoasResponseArrivals(arrivals)))
              }
            case Left(invalidResponse) =>
              handleNon2xx(invalidResponse)
          }
      }
    }
}
