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
import controllers.actions.{AuthAction, ValidateArrivalNotificationAction}
import javax.inject.Inject
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import utils.{ResponseHelper, Utils}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import scala.xml.NodeSeq
import uk.gov.hmrc.http.HttpErrorFunctions

class ArrivalMovementController @Inject()(cc: ControllerComponents,
                                   authAction: AuthAction,
                                   arrivalConnector: ArrivalConnector,
                                   validateArrivalNotificationAction: ValidateArrivalNotificationAction)(implicit ec: ExecutionContext) extends BackendController(cc) with HttpErrorFunctions with ResponseHelper {

  def createArrivalNotification(): Action[NodeSeq] = (authAction andThen validateArrivalNotificationAction).async(parse.xml) {
    implicit request =>
      arrivalConnector.postWithHeaders(request.body.toString, request.headers).map { response =>
        response.status match {
          case s if is2xx(s) =>
            response.header(LOCATION) match {
              case Some(locationValue) =>
                  Accepted.withHeaders(LOCATION -> s"/customs/transits/movements/arrivals/${Utils.urlEncode(Utils.lastFragment(locationValue))}")
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
          case s if is2xx(s) =>
            response.header(LOCATION) match {
              case Some(locationValue) =>
                  Accepted.withHeaders(LOCATION -> s"/customs/transits/movements/arrivals/${Utils.urlEncode(Utils.lastFragment(locationValue))}")
              case _ =>
                InternalServerError
            }
          case _ => handleNon2xx(response)
        }
      }
  }
}
