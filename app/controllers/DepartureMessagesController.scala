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

import connectors.DepartureMessageConnector
import controllers.actions.{AuthAction, ValidateAcceptJsonHeaderAction, ValidateDepartureMessageAction}
import models.MessageType

import javax.inject.Inject
import models.response.{HateaosDepartureMessagesPostResponseMessage, HateaosDepartureResponseMessage, HateaosResponseDepartureWithMessages}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import utils.{ResponseHelper, Utils}

import scala.concurrent.ExecutionContext
import scala.xml.NodeSeq

class DepartureMessagesController @Inject()(cc: ControllerComponents,
                                            authAction: AuthAction,
                                            messageConnector: DepartureMessageConnector,
                                            validateMessageAction: ValidateDepartureMessageAction,
                                            validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction)(implicit ec: ExecutionContext) extends BackendController(cc) with HttpErrorFunctions with ResponseHelper {

  def sendMessageDownstream(departureId: String): Action[NodeSeq] = (authAction andThen validateMessageAction).async(parse.xml) {
    implicit request =>
      messageConnector.post(request.body.toString, departureId).map { response =>
        response.status match {
          case s if is2xx(s) =>
            response.header(LOCATION) match {
              case Some(locationValue) =>
                MessageType.getMessageType(request.body) match {
                  case Some(messageType: MessageType) =>
                    Accepted(Json.toJson(HateaosDepartureMessagesPostResponseMessage(
                      departureId,
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

  def getDepartureMessages(departureId: String): Action[AnyContent] =
    (authAction andThen validateAcceptJsonHeaderAction).async {
      implicit request => {
        messageConnector.getMessages(departureId).map {
          case Right(d) => {
            Ok(Json.toJson(HateaosResponseDepartureWithMessages(d)))
          }
          case Left(response) =>
            handleNon2xx(response)
        }
      }
    }

  def getDepartureMessage(departureId: String, messageId: String): Action[AnyContent] =
    (authAction andThen validateAcceptJsonHeaderAction).async {
      implicit request => {
        messageConnector.get(departureId, messageId).map {
          case Right(m) =>
            Ok(Json.toJson(HateaosDepartureResponseMessage(departureId, messageId, m)))
          case Left(response) =>
            handleNon2xx(response)
        }
      }
    }
}
