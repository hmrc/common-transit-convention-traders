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

import connectors.DeparturesConnector
import controllers.actions.{AuthAction, ValidateAcceptJsonHeaderAction, ValidateDepartureDeclarationAction}
import javax.inject.Inject
import models.response.ResponseDeparture
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import utils.{ResponseHelper, Utils}
import utils.CallOps._

import scala.concurrent.ExecutionContext
import scala.xml.NodeSeq

class DeparturesController @Inject()(cc: ControllerComponents,
                                     authAction: AuthAction,
                                     departuresConnector: DeparturesConnector,
                                     validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction,
                                     validateDepartureDeclarationAction: ValidateDepartureDeclarationAction)(implicit ec: ExecutionContext) extends BackendController(cc) with HttpErrorFunctions with ResponseHelper {

  def submitDeclaration(): Action[NodeSeq] = (authAction andThen validateDepartureDeclarationAction).async(parse.xml) {
    implicit request =>
      departuresConnector.post(request.body.toString).map { response =>
        response.status match {
          case status if is2xx(status) =>
            response.header(LOCATION) match {
              case Some(locationValue) =>
                Accepted.withHeaders(LOCATION -> routes.DeparturesController.getDeparture(Utils.lastFragment(locationValue)).urlWithContext)
              case _ => InternalServerError
            }
          case _ => handleNon2xx(response)
        }

      }
  }

  def getDeparture(departureId: String): Action[AnyContent] = (authAction andThen validateAcceptJsonHeaderAction).async {
      implicit request => {
        departuresConnector.get(departureId).map { result =>
          result match {
            case Right(departure) => Ok(Json.toJson(ResponseDeparture(departure)))
            case Left(invalidResponse) => handleNon2xx(invalidResponse)
          }
        }
      }
    }

  def getDepartureMessages(departureId: String): Action[AnyContent] = ???
}