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

import java.net.{URI, URLEncoder}

import connectors.{ArrivalConnector, MessageConnector}
import controllers.actions.AuthAction
import javax.inject.Inject
import models.request.ArrivalNotificationXSD
import play.api.mvc.{Action, ControllerComponents}
import services.XmlValidationService
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

class ArrivalsController @Inject()(cc: ControllerComponents,
                                   authAction: AuthAction,
                                   messageConnector: MessageConnector,
                                   arrivalConnector: ArrivalConnector,
                                   xmlValidationService: XmlValidationService)(implicit ec: ExecutionContext) extends BackendController(cc) {

  def createArrivalNotification(): Action[NodeSeq] = authAction.async(parse.xml) {
    implicit request =>
      xmlValidationService.validate(request.body.toString, ArrivalNotificationXSD) match {
        case Right(_) =>
          messageConnector.post(request.body).map { response =>
            response.status match {
              case NO_CONTENT =>
                val location = response.header(LOCATION)

                location match {
                  case Some(locationValue) => parseArrivalIdFromLocation(locationValue) match {
                    case Success(id) =>
                      Accepted.withHeaders(LOCATION -> s"/movements/arrivals/${urlEncode(id)}")
                    case Failure(_) =>
                      InternalServerError
                  }
                  case _ =>
                    InternalServerError
                }
            }
          } recover {
            case _: Throwable =>
              InternalServerError
          }
        case Left(_) =>
          Future.successful(BadRequest)
      }
  }

  def resubmitArrivalNotification(arrivalId: String): Action[NodeSeq] = authAction.async(parse.xml) {
    implicit request =>
      xmlValidationService.validate(request.body.toString, ArrivalNotificationXSD) match {
        case Right(_) =>
            arrivalConnector.put(arrivalId, request.body).map { response =>
              response.status match {
                case NO_CONTENT =>
                  val location = response.header(LOCATION)

                  location match {
                    case Some(locationValue) => parseArrivalIdFromLocation(locationValue) match {
                      case Success(id) =>
                        Accepted.withHeaders(LOCATION -> s"/movements/arrivals/${urlEncode(id)}")
                      case Failure(_) =>
                        InternalServerError
                    }
                    case _ => InternalServerError
                  }
              }
            } recover {
              case _: Throwable =>
                InternalServerError
            }
        case Left(_) =>
          Future.successful(BadRequest)
      }
  }

  private def parseArrivalIdFromLocation(location: String): Try[String] =
    Try(new URI(location).getPath.split("/").last)

  private def urlEncode(s: String): String =
    URLEncoder.encode(s, "UTF-8")
}
