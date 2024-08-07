/*
 * Copyright 2023 HM Revenue & Customs
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

import config.Constants
import connectors.PushPullNotificationConnector
import javax.inject.Inject
import models.response.JsonClientErrorResponse
import models.response.JsonSystemErrorResponse
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.ResponseHelper
import models.response.HateoasResponseBox

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class PushPullNotificationController @Inject() (cc: ControllerComponents, pushPullNotificationConnector: PushPullNotificationConnector)(implicit
  ec: ExecutionContext
) extends BackendController(cc)
    with HttpErrorFunctions
    with ResponseHelper {

  def getBoxInfo: Action[AnyContent] = Action.async {
    implicit request =>
      request.headers.get(Constants.XClientIdHeader) match {
        case Some(clientId) =>
          pushPullNotificationConnector.getBox(clientId).map {
            case Left(error) if error.statusCode == NOT_FOUND =>
              NotFound(Json.toJson(JsonClientErrorResponse(NOT_FOUND, "No box found for your client id")))
            case Left(_)    => InternalServerError(Json.toJson(JsonSystemErrorResponse(INTERNAL_SERVER_ERROR, "Unexpected Error")))
            case Right(box) => Ok(Json.toJson(HateoasResponseBox(box)))
          }
        case None => Future.successful(BadRequest(Json.toJson(JsonClientErrorResponse(BAD_REQUEST, "Client Id Required"))))
      }
  }
}
