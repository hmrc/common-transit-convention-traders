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

import akka.stream.Materializer
import audit.AuditService
import audit.AuditType
import com.google.inject.ImplementedBy
import com.google.inject.Singleton
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import config.Constants.MissingECCEnrolmentMessage
import config.Constants.XMissingECCEnrolment
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
import v2.utils.CallOps._
import utils.ResponseHelper
import utils.Utils

import java.time.OffsetDateTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.xml.NodeSeq

@ImplementedBy(classOf[DeparturesController])
trait V1DeparturesController {

  def submitDeclaration(): Action[NodeSeq]
  def getDeparture(departureId: DepartureId): Action[AnyContent]
  def getDeparturesForEori(updatedSince: Option[OffsetDateTime]): Action[AnyContent]
}

@Singleton
class DeparturesController @Inject() (
  cc: ControllerComponents,
  authAction: AuthAction,
  departuresConnector: DeparturesConnector,
  validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction[AuthRequest],
  validateDepartureDeclarationAction: ValidateDepartureDeclarationAction[AuthRequest],
  ensureGuaranteeAction: EnsureGuaranteeAction,
  auditService: AuditService,
  messageAnalyser: AnalyseMessageActionProvider,
  val metrics: Metrics,
  config: AppConfig
)(implicit ec: ExecutionContext, val materializer: Materializer)
    extends BackendController(cc)
    with HasActionMetrics
    with HttpErrorFunctions
    with ResponseHelper
    with V1DeparturesController {

  import MetricsKeys.Endpoints._

  lazy val departuresCount = histo(GetDeparturesForEoriCount)

  override def submitDeclaration(): Action[NodeSeq] =
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
                      if (config.phase4EnrolmentHeader && !request.request.hasNewEnrolment) {
                        Accepted(Json.toJson(HateoasDeparturePostResponseMessage(departureId, messageType.code, request.body, response.responseData)))
                          .withHeaders(
                            LOCATION             -> routing.routes.DeparturesRouter.getDeparture(departureId.toString).urlWithContext,
                            XMissingECCEnrolment -> MissingECCEnrolmentMessage
                          )
                      } else {
                        Accepted(Json.toJson(HateoasDeparturePostResponseMessage(departureId, messageType.code, request.body, response.responseData)))
                          .withHeaders(LOCATION -> routing.routes.DeparturesRouter.getDeparture(departureId.toString).urlWithContext)
                      }
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
              if (config.phase4EnrolmentHeader && !request.hasNewEnrolment) {
                Ok(Json.toJson(HateoasResponseDeparture(departure)))
                  .withHeaders(XMissingECCEnrolment -> MissingECCEnrolmentMessage)
              } else {
                Ok(Json.toJson(HateoasResponseDeparture(departure)))
              }
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
              if (config.phase4EnrolmentHeader && !request.hasNewEnrolment) {
                Ok(Json.toJson(HateoasResponseDepartures(departures)))
                  .withHeaders(XMissingECCEnrolment -> MissingECCEnrolmentMessage)
              } else {
                Ok(Json.toJson(HateoasResponseDepartures(departures)))
              }
            case Left(invalidResponse) =>
              handleNon2xx(invalidResponse)
          }
      }
    }

}
