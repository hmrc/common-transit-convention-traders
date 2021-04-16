/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.Inject

import audit.AuditService
import audit.AuditType
import com.kenshoo.play.metrics.Metrics
import connectors.DeparturesConnector
import controllers.actions.AuthAction
import controllers.actions.EnsureGuaranteeAction
import controllers.actions.ValidateAcceptJsonHeaderAction
import controllers.actions.ValidateDepartureDeclarationAction
import metrics.HasActionMetrics
import models.MessageType
import models.response.HateaosDeparturePostResponseMessage
import models.response.HateaosResponseDeparture
import models.response.HateaosResponseDepartures
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.CallOps._
import utils.ResponseHelper
import utils.Utils

import scala.concurrent.ExecutionContext
import scala.xml.NodeSeq

class DeparturesController @Inject() (
  cc: ControllerComponents,
  authAction: AuthAction,
  departuresConnector: DeparturesConnector,
  validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction,
  validateDepartureDeclarationAction: ValidateDepartureDeclarationAction,
  ensureGuaranteeAction: EnsureGuaranteeAction,
  auditService: AuditService,
  val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with HasActionMetrics
    with HttpErrorFunctions
    with ResponseHelper {

  def submitDeclaration(): Action[NodeSeq] =
    withMetricsTimerAction("submit-departure-declaration") {
      (authAction andThen validateDepartureDeclarationAction andThen ensureGuaranteeAction).async(parse.xml) {
        implicit request =>
          departuresConnector.post(request.newXml.toString).map {
            response =>
              response.status match {
                case status if is2xx(status) =>
                  response.header(LOCATION) match {
                    case Some(locationValue) =>
                      if (request.guaranteeAdded) {
                        auditService.auditEvent(AuditType.TenThousandEuroGuaranteeAdded, request.newXml)
                      }
                      MessageType.getMessageType(request.body) match {
                        case Some(messageType: MessageType) =>
                          val departureId = Utils.lastFragment(locationValue)
                          Accepted(
                            Json.toJson(
                              HateaosDeparturePostResponseMessage(
                                departureId,
                                messageType.code,
                                request.body
                              )
                            )
                          ).withHeaders(LOCATION -> routes.DeparturesController.getDeparture(departureId).urlWithContext)
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
    }

  def getDeparture(departureId: String): Action[AnyContent] =
    withMetricsTimerAction("get-departure") {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          departuresConnector.get(departureId).map {
            case Right(departure) =>
              Ok(Json.toJson(HateaosResponseDeparture(departure)))
            case Left(invalidResponse) =>
              handleNon2xx(invalidResponse)
          }
      }
    }

  def getDeparturesForEori: Action[AnyContent] =
    withMetricsTimerAction("get-departures-for-eori") {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          departuresConnector.getForEori.map {
            case Right(departures) =>
              Ok(Json.toJson(HateaosResponseDepartures(departures)))
            case Left(invalidResponse) =>
              handleNon2xx(invalidResponse)
          }
      }
    }
}
