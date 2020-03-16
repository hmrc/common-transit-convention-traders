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
import controllers.actions.AuthAction
import javax.inject.Inject
import models.request.ArrivalNotificationXSD
import play.api.mvc.{Action, ControllerComponents}
import services.XmlValidationService
import uk.gov.hmrc.auth.core.InsufficientEnrolments
import uk.gov.hmrc.http.Upstream5xxResponse
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq


class ArrivalsController @Inject()(cc: ControllerComponents,
                                   authAction: AuthAction,
                                   messageConnector: MessageConnector,
                                   xmlValidationService: XmlValidationService)(implicit ec: ExecutionContext) extends BackendController(cc) {
  def createArrivalNotification(): Action[NodeSeq] = authAction.async(parse.xml) {
    implicit request =>
      xmlValidationService.validate(request.body.toString, ArrivalNotificationXSD) match {
        case Right(_) =>
          messageConnector.post(request.body).map { response =>
            response.status match {
              case NO_CONTENT => Accepted
            }
          } recover {
            case _: Throwable => InternalServerError
          }
        case Left(_) =>
          Future.successful(BadRequest)
      }
  }
}
