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

import connectors.ArrivalConnector
import controllers.actions.AuthAction
import javax.inject.Inject
import models.request.ArrivalNotificationXSD
import play.api.mvc.{Action, ControllerComponents}
import services.XmlValidationService
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import utils.Utils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.xml.NodeSeq
import uk.gov.hmrc.http.{HttpErrorFunctions, Upstream4xxResponse}

class ArrivalMovementController @Inject()(cc: ControllerComponents,
                                   authAction: AuthAction,
                                   arrivalConnector: ArrivalConnector,
                                   xmlValidationService: XmlValidationService)(implicit ec: ExecutionContext) extends BackendController(cc) with HttpErrorFunctions {

  def createArrivalNotification(): Action[NodeSeq] = authAction.async(parse.xml) {
    implicit request =>
      xmlValidationService.validate(request.body.toString, ArrivalNotificationXSD) match {
        case Right(_) =>
          arrivalConnector.post(request.body.toString).map { response =>
            response.status match {
              case s if is2xx(s) =>
                response.header(LOCATION) match {
                  case Some(locationValue) => Utils.arrivalId(locationValue) match {
                    case Success(id) =>
                      Accepted.withHeaders(LOCATION -> s"/customs/transits/movements/arrivals/${Utils.urlEncode(id)}")
                    case Failure(_) =>
                      InternalServerError
                  }
                  case _ =>
                    InternalServerError
                }
              case _ => Status(response.status)
            }
          }
        case Left(_) =>
          Future.successful(BadRequest)
      }
  }

  def resubmitArrivalNotification(arrivalId: String): Action[NodeSeq] = authAction.async(parse.xml) {
    implicit request =>
      xmlValidationService.validate(request.body.toString, ArrivalNotificationXSD) match {
        case Right(_) =>
          arrivalConnector.put(request.body.toString, arrivalId).map { response =>
            response.status match {
              case s if is2xx(s) =>
                response.header(LOCATION) match {
                  case Some(locationValue) => Utils.arrivalId(locationValue) match {
                    case Success(id) =>
                      Accepted.withHeaders(LOCATION -> s"/customs/transits/movements/arrivals/${Utils.urlEncode(id)}")
                    case Failure(_) =>
                      InternalServerError
                  }
                  case _ =>
                    InternalServerError
                }
              case _ => Status(response.status)
            }
          }
        case Left(_) =>
          Future.successful(BadRequest)
      }
  }
}
