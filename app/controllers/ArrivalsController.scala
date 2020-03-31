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
import models.request.{ArrivalNotificationXSD, UnloadingRemarksXSD, XSDFile}
import play.api.mvc.{Action, ControllerComponents, Result}
import services.XmlValidationService
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import utils.Utils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, Upstream4xxResponse}

class ArrivalsController @Inject()(cc: ControllerComponents,
                                   authAction: AuthAction,
                                   messageConnector: MessageConnector,
                                   arrivalConnector: ArrivalConnector,
                                   xmlValidationService: XmlValidationService)(implicit ec: ExecutionContext) extends BackendController(cc) {

  def createUnloadingPermission(arrivalId: String): Action[NodeSeq] = authAction.async(parse.xml) {
    implicit request =>
      postToArrivalEndpointDownstream(request.body.toString, UnloadingRemarksXSD, Some(arrivalId))
  }

  private def postToArrivalEndpointDownstream(body: String, xsdFile: XSDFile, arrivalId: Option[String])(implicit hc: HeaderCarrier): Future[Result] =
    xmlValidationService.validate(body, xsdFile) match {
      case Right(_) =>
        messageConnector.post(body, arrivalId).map { response =>
          response.status match {
            case s if Utils.is2xx(s) =>
              val location = response.header(LOCATION)

              location match {
                case Some(locationValue) => Utils.arrivalId(locationValue) match {
                  case Success(id) =>
                    Accepted.withHeaders(LOCATION -> s"/customs/transits/movements/arrivals/${Utils.urlEncode(id)}")
                  case Failure(_) =>
                    InternalServerError
                }
                case _ =>
                  InternalServerError
              }
          }
        } recover {
          case e: Upstream4xxResponse =>
            if (e.upstreamResponseCode == 400)
              BadRequest
            else if (e.upstreamResponseCode == 404)
              NotFound
            else
              InternalServerError
          case _: Throwable =>
            InternalServerError
        }
      case Left(_) =>
        Future.successful(BadRequest)
    }

  def createArrivalNotification(): Action[NodeSeq] = authAction.async(parse.xml) {
    implicit request =>
      xmlValidationService.validate(request.body.toString, ArrivalNotificationXSD) match {
        case Right(_) =>
          messageConnector.post(request.body).map { response =>
            response.status match {
              case NO_CONTENT =>
                getLocationHeader(response) match {
                  case Some(lh) =>
                    Accepted.withHeaders(LOCATION -> s"/movements/arrivals/${urlEncode(lh.arrivalId)}")
                  case _ => InternalServerError
                }
              case _ => InternalServerError
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
                  getLocationHeader(response) match {
                    case Some(lh) =>
                      Accepted.withHeaders(LOCATION -> s"/movements/arrivals/${urlEncode(lh.arrivalId)}")
                    case _ => InternalServerError
                  }
                case _ => InternalServerError
              }
            } recover {
              case _: Throwable =>
                InternalServerError
            }
        case Left(_) =>
          Future.successful(BadRequest)
      }
  }

  private def getLocationHeader(r: HttpResponse): Option[LocationHeader] =
  {
    r.header(LOCATION).map(l => LocationHeader.parse(l))
  }

  private def urlEncode(s: String): String =
    URLEncoder.encode(s, "UTF-8")
}
