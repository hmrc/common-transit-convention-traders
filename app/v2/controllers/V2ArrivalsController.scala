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

package v2.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import com.kenshoo.play.metrics.Metrics
import metrics.HasActionMetrics
import metrics.MetricsKeys.Endpoints.GetArrivalsForEori
import models.domain.Arrivals
import models.response.HateoasResponseArrivals
import play.api.Logging
import play.api.http.MimeTypes
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc._
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.controllers.actions.providers.MessageSizeActionProvider
import v2.controllers.request.AuthenticatedRequest
import v2.controllers.stream.StreamingParsers
import v2.models.errors.PresentationError
import v2.models.AuditType
import v2.models.MovementId
import v2.models.MovementType
import v2.models.request.MessageType
import v2.models.responses.ArrivalResponse
import v2.models.responses.hateoas._
import v2.services._

import java.time.OffsetDateTime
import scala.concurrent.Future

@ImplementedBy(classOf[V2ArrivalsControllerImpl])
trait V2ArrivalsController {
  def createArrivalNotification(): Action[Source[ByteString, _]]
  def getArrival(arrivalId: MovementId): Action[AnyContent]
  def getArrivalsForEori(updatedSince: Option[OffsetDateTime]): Action[AnyContent]
}

@Singleton
class V2ArrivalsControllerImpl @Inject() (
  val controllerComponents: ControllerComponents,
  authActionNewEnrolmentOnly: AuthNewEnrolmentOnlyAction,
  validationService: ValidationService,
  arrivalsService: ArrivalsService,
  routerService: RouterService,
  auditService: AuditingService,
  conversionService: ConversionService,
  pushNotificationsService: PushNotificationsService,
  messageSizeAction: MessageSizeActionProvider,
  val metrics: Metrics
)(implicit val materializer: Materializer, val temporaryFileCreator: TemporaryFileCreator)
    extends BaseController
    with V2ArrivalsController
    with Logging
    with StreamingParsers
    with VersionedRouting
    with ErrorTranslator
    with ContentTypeRouting
    with HasActionMetrics {

  def createArrivalNotification(): Action[Source[ByteString, _]] =
    contentTypeRoute {
      case Some(MimeTypes.XML)  => createArrivalNotificationXML()
      case Some(MimeTypes.JSON) => createArrivalNotificationJSON()
    }

  def createArrivalNotificationXML(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- validationService.validateXml(MessageType.ArrivalNotification, request.body).asPresentation
          _ = auditService.audit(AuditType.ArrivalNotification, request.body, MimeTypes.XML)
          arrivalResult <- persistAndSend(request.body)
        } yield arrivalResult).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          result => Accepted(HateoasArrivalNotificationResponse(result.arrivalId))
        )
    }

  def createArrivalNotificationJSON(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          xmlSource     <- handleJson(MessageType.ArrivalNotification, request.body)
          arrivalResult <- handleXml(xmlSource)
        } yield arrivalResult).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          result => Accepted(HateoasArrivalNotificationResponse(result.arrivalId))
        )
    }

  def handleJson(messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier
  ): EitherT[Future, PresentationError, Source[ByteString, _]] =
    for {
      _ <- validationService.validateJson(messageType, source).asPresentation
      _ = auditService.audit(messageType.auditType, source, MimeTypes.JSON)
      xmlSource <- conversionService.jsonToXml(messageType, source).asPresentation
    } yield xmlSource

  def handleXml(src: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[Source[ByteString, _]]
  ): EitherT[Future, PresentationError, ArrivalResponse] =
    withReusableSource(src) {
      xmlSource =>
        for {
          _ <- validationService
            .validateXml(MessageType.ArrivalNotification, xmlSource)
            .asPresentation(jsonToXmlValidationErrorConverter, materializerExecutionContext)
          arrivalResult <- persistAndSend(xmlSource)
        } yield arrivalResult
    }

  def getArrivalsForEori(updatedSince: Option[OffsetDateTime]): Action[AnyContent] =
    authActionNewEnrolmentOnly.async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        arrivalsService
          .getArrivalsForEori(request.eoriNumber)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasArrivalIdsResponse(response)))
          )
    }

  private def persistAndSend(
    source: Source[ByteString, _]
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[Source[ByteString, _]]): EitherT[Future, PresentationError, ArrivalResponse] =
    for {
      arrivalResult <- arrivalsService.createArrival(request.eoriNumber, source).asPresentation
      _ = pushNotificationsService.associate(arrivalResult.arrivalId, MovementType.Arrival, request.headers)
      _ <- routerService
        .send(MessageType.ArrivalNotification, request.eoriNumber, arrivalResult.arrivalId, arrivalResult.messageId, source)
        .asPresentation
    } yield arrivalResult

  def getArrival(arrivalId: MovementId): Action[AnyContent] = authActionNewEnrolmentOnly.async {
    implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

      arrivalsService
        .getArrival(request.eoriNumber, arrivalId)
        .asPresentation
        .fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Ok(Json.toJson(HateoasMovementResponse(arrivalId, response, MovementType.Arrival)))
        )
  }
}
