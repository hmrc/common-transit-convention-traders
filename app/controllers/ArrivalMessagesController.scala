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

import connectors.ArrivalMessageConnector
import controllers.actions.{AuthAction, ValidateAcceptJsonHeaderAction, ValidateArrivalMessageAction}
import javax.inject.Inject
import models.response.{ResponseArrivalWithMessages, ResponseMessage}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import utils.CallOps._
import utils.{ResponseHelper, Utils}

import scala.concurrent.ExecutionContext
import scala.xml.NodeSeq

class ArrivalMessagesController @Inject()(cc: ControllerComponents,
                                          authAction: AuthAction,
                                          messageConnector: ArrivalMessageConnector,
                                          validateMessageAction: ValidateArrivalMessageAction,
                                          validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction)(implicit ec: ExecutionContext) extends BackendController(cc) with HttpErrorFunctions with ResponseHelper
{

  def sendMessageDownstream(arrivalId: String): Action[NodeSeq] = (authAction andThen validateMessageAction).async(parse.xml) {
    implicit request =>
      messageConnector.post(request.body.toString, arrivalId).map { response =>
        response.status match {
          case s if is2xx(s) =>
            response.header(LOCATION) match {
              case Some(locationValue) =>
                  Accepted.withHeaders(LOCATION -> routes.ArrivalMessagesController.getArrivalMessage(arrivalId, Utils.lastFragment(locationValue)).urlWithContext)
              case _ =>
                InternalServerError
            }
          case _ => handleNon2xx(response)
        }
      }
  }

  def getArrivalMessage(arrivalId: String, messageId: String): Action[AnyContent] =
    (authAction andThen validateAcceptJsonHeaderAction).async {
    implicit request => {
      messageConnector.get(arrivalId, messageId).map { r =>
        r match {
          case Right(m) => Ok(Json.toJson(ResponseMessage(m, routes.ArrivalMessagesController.getArrivalMessage(arrivalId, messageId))))
          case Left(response) => handleNon2xx(response)
        }
      }
    }
  }

  def getArrivalMessages(arrivalId: String): Action[AnyContent] =
    (authAction andThen validateAcceptJsonHeaderAction).async {
      implicit request => {
          messageConnector.getMessages(arrivalId).map { r =>
            r match {
              case Right(a) => {
                Ok(Json.toJson(ResponseArrivalWithMessages(a)))
              }
              case Left(response) => handleNon2xx(response)
          }
        }
      }
    }
}
