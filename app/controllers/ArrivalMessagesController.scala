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

import connectors.MessageConnector
import controllers.actions.{AuthAction, ValidateAcceptJsonHeaderAction, ValidateMessageAction}
import javax.inject.Inject
import models.response.{ResponseArrival, ResponseMessage}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.http.{HttpErrorFunctions, HttpResponse}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import utils.{ResponseHelper, Utils}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.xml.NodeSeq

class ArrivalMessagesController @Inject()(cc: ControllerComponents,
                                   authAction: AuthAction,
                                   messageConnector: MessageConnector,
                                   validateMessageAction: ValidateMessageAction,
                                   validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction)(implicit ec: ExecutionContext) extends BackendController(cc) with HttpErrorFunctions with ResponseHelper
{

  def sendMessageDownstream(arrivalId: String): Action[NodeSeq] = (authAction andThen validateMessageAction).async(parse.xml) {
    implicit request =>
      messageConnector.post(request.body.toString, arrivalId).map { response =>
        response.status match {
          case s if is2xx(s) =>
            response.header(LOCATION) match {
              case Some(locationValue) => Utils.lastFragment(locationValue) match {
                case Success(id) =>
                  Accepted.withHeaders(LOCATION -> s"/customs/transits/movements/arrivals/${Utils.urlEncode(arrivalId)}/messages/${Utils.urlEncode(id)}")
                case Failure(_) =>
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
      messageConnector.get(arrivalId, messageId).map { r =>
        r match {
          case Right(m) => Ok(Json.toJson(ResponseMessage(m).copy(location = s"/movements/arrivals/${Utils.urlEncode(arrivalId)}/messages/${Utils.urlEncode(messageId)}")))
          case Left(response) => handleNon2xx(response)
        }
      }
    }
  }

  def getArrivalMessages(arrivalId: String): Action[AnyContent] =
    (authAction andThen validateAcceptJsonHeaderAction).async {
      implicit request => {
          messageConnector.getArrivalMessages(arrivalId).map { r =>
            r match {
              case Right(a) => {

                try {
                  val messages = a.messages.map { m =>
                    Utils.lastFragment(m.location) match {
                      case Failure(_) => throw new Exception("parsing location header failed")
                      case Success(value) => ResponseMessage(m) copy (location = s"/movements/arrivals/${Utils.urlEncode(arrivalId)}/messages/$value")
                    }
                  }
                  Ok(Json.toJson(ResponseArrival(a).copy(arrival = s"/movements/arrivals/${Utils.urlEncode(arrivalId)}", messages = messages)))
                }
                catch {
                  case _ => InternalServerError
                }

              }
              case Left(response) => handleNon2xx(response)

          }
        }
      }
    }
}
