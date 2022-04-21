/*
 * Copyright 2022 HM Revenue & Customs
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

import audit.AuditService
import audit.AuditType
import com.kenshoo.play.metrics.Metrics
import connectors.DeparturesConnector
import controllers.actions._
import metrics.HasActionMetrics
import metrics.MetricsKeys
import models.MessageType
import models.domain.DepartureId
import models.response.HateoasDeparturePostResponseMessage
import models.response.HateoasResponseDeparture
import models.response.HateoasResponseDepartures
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.CallOps._
import utils.ResponseHelper
import utils.Utils

import java.time.OffsetDateTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.xml.NodeSeq

class DeparturesController @Inject() (
  cc: ControllerComponents,
  authAction: AuthAction,
  authActionNewEnrolmentOnly: AuthActionNewEnrolmentOnly,
  departuresConnector: DeparturesConnector,
  validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction,
  validateDepartureDeclarationAction: ValidateDepartureDeclarationAction,
  ensureGuaranteeAction: EnsureGuaranteeAction,
  auditService: AuditService,
  messageAnalyser: AnalyseMessageActionProvider,
  val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with HasActionMetrics
    with HttpErrorFunctions
    with ResponseHelper
    with VersionSwitch {

  import MetricsKeys.Endpoints._

  lazy val departuresCount = histo(GetDeparturesForEoriCount)

  def submitDeclaration(): Action[NodeSeq] =
    versionSwitch(submitDeclarationVersionOne, submitDeclarationVersionTwo)

  private def submitDeclarationVersionOne(): Action[NodeSeq] =
    withMetricsTimerAction(SubmitDepartureDeclaration) {
      (authAction andThen validateDepartureDeclarationAction andThen messageAnalyser() andThen ensureGuaranteeAction).async(parse.xml) {
        implicit request =>
          departuresConnector.post(request.newXml.toString).map {
            case Right(response) =>
              response.header(LOCATION) match {
                case Some(locationValue) =>
                  if (request.guaranteeAdded) {
                    auditService.auditEvent(AuditType.TenThousandEuroGuaranteeAdded, request.newXml)
                  }
                  MessageType.getMessageType(request.body) match {
                    case Some(messageType: MessageType) =>
                      val departureId = DepartureId(Utils.lastFragment(locationValue).toInt)
                      Accepted(
                        Json.toJson(
                          HateoasDeparturePostResponseMessage(
                            departureId,
                            messageType.code,
                            request.body,
                            response.responseData
                          )
                        )
                      ).withHeaders(LOCATION -> routes.DeparturesController.getDeparture(departureId).urlWithContext)
                    case None =>
                      InternalServerError
                  }
                case _ =>
                  InternalServerError
              }
            case Left(response) => handleNon2xx(response)
          }
      }
    }

  def getDeparture(departureId: DepartureId): Action[AnyContent] =
    withMetricsTimerAction(GetDeparture) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          departuresConnector.get(departureId).map {
            case Right(departure) =>
              Ok(Json.toJson(HateoasResponseDeparture(departure)))
            case Left(invalidResponse) =>
              handleNon2xx(invalidResponse)
          }
      }
    }

  def getDeparturesForEori(updatedSince: Option[OffsetDateTime]): Action[AnyContent] =
    withMetricsTimerAction(GetDeparturesForEori) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          departuresConnector.getForEori(updatedSince).map {
            case Right(departures) =>
              departuresCount.update(departures.departures.length)
              Ok(Json.toJson(HateoasResponseDepartures(departures)))
            case Left(invalidResponse) =>
              handleNon2xx(invalidResponse)
          }
      }
    }

  private def submitDeclarationVersionTwo(): Action[NodeSeq] = authActionNewEnrolmentOnly.async(parse.xml) {
    implicit request =>
      val eori = request.eori //TBD: EORI to be sent on to the transit-movements service
      logger.info("Version 2 of endpoint has been called")
      Future.successful(Accepted)
  }
}
