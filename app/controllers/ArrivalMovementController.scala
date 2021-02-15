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

import connectors.ArrivalConnector
import controllers.actions.{AuthAction, ValidateAcceptJsonHeaderAction, ValidateArrivalNotificationAction}
import models.MessageType
import models.domain.Arrivals

import javax.inject.Inject
import models.response.{HateaosArrivalMovementPostResponseMessage, HateaosResponseArrival, HateaosResponseArrivals}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import utils.CallOps._
import utils.{ResponseHelper, Utils}

import scala.concurrent.ExecutionContext
import scala.xml.NodeSeq

class ArrivalMovementController @Inject()(cc: ControllerComponents,
                                   authAction: AuthAction,
                                   arrivalConnector: ArrivalConnector,
                                   validateArrivalNotificationAction: ValidateArrivalNotificationAction,
                                   validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction)(implicit ec: ExecutionContext) extends BackendController(cc) with HttpErrorFunctions with ResponseHelper {

  def createArrivalNotification(): Action[NodeSeq] = (authAction andThen validateArrivalNotificationAction).async(parse.xml) {
    implicit request =>
      arrivalConnector.post(request.body.toString).map { response =>
        response.status match {
          case status if is2xx(status) =>
            response.header(LOCATION) match {
              case Some(locationValue: String) =>
                MessageType.getMessageType(request.body) match {
                  case Some(messageType: MessageType) =>
                    val arrivalId = Utils.lastFragment(locationValue)
                    Accepted(Json.toJson(HateaosArrivalMovementPostResponseMessage(
                      arrivalId,
                      messageType.code,
                      request.body
                    ))).withHeaders(LOCATION -> routes.ArrivalMovementController.getArrival(arrivalId).urlWithContext)
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

  def resubmitArrivalNotification(arrivalId: String): Action[NodeSeq] = (authAction andThen validateArrivalNotificationAction).async(parse.xml) {
    implicit request =>
      arrivalConnector.put(request.body.toString, arrivalId).map { response =>
        response.status match {
          case status if is2xx(status) =>
            response.header(LOCATION) match {
              case Some(locationValue: String) =>
                MessageType.getMessageType(request.body) match {
                  case Some(messageType: MessageType) =>
                    val arrivalId = Utils.lastFragment(locationValue)
                    Accepted(Json.toJson(HateaosArrivalMovementPostResponseMessage(
                      arrivalId,
                      messageType.code,
                      request.body
                    ))).withHeaders(LOCATION -> routes.ArrivalMovementController.getArrival(arrivalId).urlWithContext)
                  case None =>
                    InternalServerError
                }
              case _ =>
                InternalServerError
            }
          case _ =>
            handleNon2xx(response)
        }
      }
  }

  def getArrival(arrivalId: String): Action[AnyContent] =
    (authAction andThen validateAcceptJsonHeaderAction).async {
      implicit request => {
        arrivalConnector.get(arrivalId).map {
          case Right(arrival) =>
            Ok(Json.toJson(HateaosResponseArrival(arrival)))
          case Left(invalidResponse) =>
            handleNon2xx(invalidResponse)
        }
      }
    }

  def getArrivalsForEori: Action[AnyContent] =
    (authAction andThen validateAcceptJsonHeaderAction).async {
      implicit request => {
        arrivalConnector.getForEori.map {
          case Right(arrivals: Arrivals) =>
            Ok(Json.toJson(HateaosResponseArrivals(arrivals)))
          case Left(invalidResponse) =>
            handleNon2xx(invalidResponse)
        }
      }
    }
}
