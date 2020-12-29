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
import models.MessageType

import javax.inject.Inject
import models.response.{HateaosArrivalMessagesPostResponseMessage, HateaosArrivalResponseMessage, HateaosResponseArrivalWithMessages}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import utils.{ResponseHelper, Utils}

import scala.concurrent.ExecutionContext
import scala.xml.NodeSeq

class ArrivalMessagesController @Inject()(cc: ControllerComponents,
                                          authAction: AuthAction,
                                          messageConnector: ArrivalMessageConnector,
                                          validateMessageAction: ValidateArrivalMessageAction,
                                          validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction)(implicit ec: ExecutionContext) extends BackendController(cc) with HttpErrorFunctions with ResponseHelper {

  def sendMessageDownstream(arrivalId: String): Action[NodeSeq] = (authAction andThen validateMessageAction).async(parse.xml) {
    implicit request =>
      messageConnector.post(request.body.toString, arrivalId).map { response =>
        response.status match {
          case s if is2xx(s) =>
            response.header(LOCATION) match {
              case Some(locationValue) =>
                MessageType.getMessageType(request.body) match {
                  case Some(messageType: MessageType) =>
                    Accepted(Json.toJson(HateaosArrivalMessagesPostResponseMessage(
                      arrivalId,
                      Utils.lastFragment(locationValue),
                      messageType.code,
                      request.body
                    )))
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

  def getArrivalMessage(arrivalId: String, messageId: String): Action[AnyContent] =
    (authAction andThen validateAcceptJsonHeaderAction).async {
      implicit request => {
        messageConnector.get(arrivalId, messageId).map {
          case Right(m) =>
            Ok(Json.toJson(HateaosArrivalResponseMessage(arrivalId, messageId, m)))
          case Left(response) =>
            handleNon2xx(response)
        }
      }
    }

  def getArrivalMessages(arrivalId: String): Action[AnyContent] =
    (authAction andThen validateAcceptJsonHeaderAction).async {
      implicit request => {
        messageConnector.getMessages(arrivalId).map {
          case Right(a) => {
            Ok(Json.toJson(HateaosResponseArrivalWithMessages(a)))
          }
          case Left(response) =>
            handleNon2xx(response)
        }
      }
    }
}