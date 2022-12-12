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
import com.codahale.metrics.Counter
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import com.kenshoo.play.metrics.Metrics
import metrics.HasActionMetrics
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc._
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.controllers.actions.providers.AcceptHeaderActionProvider
import v2.controllers.actions.providers.MessageSizeActionProvider
import v2.controllers.request.AuthenticatedRequest
import v2.controllers.stream.StreamingParsers
import v2.models.AuditType
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.errors.PresentationError
import v2.models.request.MessageType
import v2.models.responses.DeclarationResponse
import v2.models.responses.UpdateMovementResponse
import v2.models.responses.hateoas._
import v2.services._
import v2.utils.PreMaterialisedFutureProvider

import java.time.OffsetDateTime
import scala.concurrent.Future

@ImplementedBy(classOf[V2DeparturesControllerImpl])
trait V2DeparturesController {
  def submitDeclaration(): Action[Source[ByteString, _]]
  def getMessage(departureId: MovementId, messageId: MessageId): Action[AnyContent]
  def getMessageIds(departureId: MovementId, receivedSince: Option[OffsetDateTime] = None): Action[AnyContent]
  def getDeparture(departureId: MovementId): Action[AnyContent]
  def getDeparturesForEori(updatedSince: Option[OffsetDateTime]): Action[AnyContent]
  def attachMessage(departureId: MovementId): Action[Source[ByteString, _]]
}

@Singleton
class V2DeparturesControllerImpl @Inject() (
  val controllerComponents: ControllerComponents,
  authActionNewEnrolmentOnly: AuthNewEnrolmentOnlyAction,
  validationService: ValidationService,
  conversionService: ConversionService,
  departuresService: DeparturesService,
  routerService: RouterService,
  auditService: AuditingService,
  pushNotificationsService: PushNotificationsService,
  messageSizeAction: MessageSizeActionProvider,
  acceptHeaderActionProvider: AcceptHeaderActionProvider,
  val metrics: Metrics,
  xmlParsingService: XmlMessageParsingService,
  jsonParsingService: JsonMessageParsingService,
  responseFormatterService: ResponseFormatterService,
  val preMaterialisedFutureProvider: PreMaterialisedFutureProvider
)(implicit val materializer: Materializer, val temporaryFileCreator: TemporaryFileCreator)
    extends BaseController
    with V2DeparturesController
    with Logging
    with StreamingParsers
    with VersionedRouting
    with ErrorTranslator
    with ContentTypeRouting
    with HasActionMetrics {

  lazy val sCounter: Counter = counter(s"success-counter")
  lazy val fCounter: Counter = counter(s"failure-counter")

  def submitDeclaration(): Action[Source[ByteString, _]] =
    contentTypeRoute {
      case Some(MimeTypes.XML)  => submitDeclarationXML()
      case Some(MimeTypes.JSON) => submitDeclarationJSON()
    }

  def submitDeclarationJSON(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        val messageType: MessageType   = MessageType.DeclarationData

        (for {
          _ <- validationService.validateJson(messageType, request.body).asPresentation
          _ = auditService.audit(AuditType.DeclarationData, request.body, MimeTypes.JSON)

          xmlSource         <- conversionService.jsonToXml(messageType, request.body).asPresentation
          declarationResult <- persistAndSendToEIS(xmlSource, messageType)
        } yield declarationResult).fold[Result](
          presentationError => {
            fCounter.inc()
            Status(presentationError.code.statusCode)(Json.toJson(presentationError))
          },
          result => {
            sCounter.inc()
            Accepted(HateoasNewMovementResponse(result.departureId, MovementType.Departure))
          }
        )
    }

  def persistAndSendToEIS(
    src: Source[ByteString, _],
    messageType: MessageType
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[Source[ByteString, _]]): EitherT[Future, PresentationError, DeclarationResponse] =
    withReusableSource(src) {
      source =>
        for {
          _      <- validationService.validateXml(messageType, source).asPresentation(jsonToXmlValidationErrorConverter, materializerExecutionContext)
          result <- persistAndSend(source)
        } yield result
    }

  def submitDeclarationXML(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- validationService.validateXml(MessageType.DeclarationData, request.body).asPresentation
          // TODO: See if we can parallelise this call with the one to persistence, below.
          // Note it's an =, not <-, as we don't care (here) for its response, once it's sent, it should be
          // non-blocking
          _ = auditService.audit(AuditType.DeclarationData, request.body, MimeTypes.XML)

          declarationResult <- persistAndSend(request.body)
        } yield declarationResult).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          result => Accepted(HateoasNewMovementResponse(result.departureId, MovementType.Departure))
        )
    }

  private def persistAndSend(
    source: Source[ByteString, _]
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[Source[ByteString, _]]) =
    for {
      declarationResult <- departuresService.saveDeclaration(request.eoriNumber, source).asPresentation
      _ = pushNotificationsService.associate(declarationResult.departureId, MovementType.Departure, request.headers)
      _ <- routerService
        .send(MessageType.DeclarationData, request.eoriNumber, declarationResult.departureId, declarationResult.messageId, source)
        .asPresentation
    } yield declarationResult

  def getMessage(movementId: MovementId, messageId: MessageId): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider()).async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          messageSummary          <- departuresService.getMessage(request.eoriNumber, movementId, messageId).asPresentation
          formattedMessageSummary <- responseFormatterService.formatMessageSummary(messageSummary, request.headers.get(HeaderNames.ACCEPT).get)
        } yield formattedMessageSummary).fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Ok(Json.toJson(HateoasMovementMessageResponse(movementId, messageId, response, MovementType.Departure)))
        )
    }

  def getMessageIds(departureId: MovementId, receivedSince: Option[OffsetDateTime]): Action[AnyContent] =
    authActionNewEnrolmentOnly.async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        departuresService
          .getMessageIds(request.eoriNumber, departureId, receivedSince)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasMovementMessageIdsResponse(departureId, response, receivedSince, MovementType.Departure)))
          )
    }

  def getDeparture(departureId: MovementId): Action[AnyContent] =
    authActionNewEnrolmentOnly.async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        departuresService
          .getDeparture(request.eoriNumber, departureId)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasMovementResponse(departureId, response, MovementType.Departure)))
          )
    }

  def getDeparturesForEori(updatedSince: Option[OffsetDateTime] = None): Action[AnyContent] =
    authActionNewEnrolmentOnly.async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        departuresService
          .getDeparturesForEori(request.eoriNumber, updatedSince)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasMovementIdsResponse(response, MovementType.Departure, updatedSince)))
          )
    }

  def attachMessage(departureId: MovementId): Action[Source[ByteString, _]] =
    contentTypeRoute {
      case Some(MimeTypes.XML)  => attachMessageXML(departureId)
      case Some(MimeTypes.JSON) => attachMessageJSON(departureId)
    }

  def attachMessageXML(departureId: MovementId): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).streamWithAwait {
      awaitFileWrite => implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          messageType <- xmlParsingService.extractMessageType(request.body, MessageType.updateMessageTypesSentByDepartureTrader).asPresentation
          _           <- awaitFileWrite
          _           <- validationService.validateXml(messageType, request.body).asPresentation
          _ = auditService.audit(messageType.auditType, request.body, MimeTypes.XML)
          declarationResult <- updateAndSendDeparture(departureId, messageType, request.body)

        } yield declarationResult).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          id => Accepted(Json.toJson(HateoasMovementUpdateResponse(departureId, id.messageId, MovementType.Departure)))
        )
    }

  def attachMessageJSON(id: MovementId): Action[Source[ByteString, _]] = {

    def handleJson(messageType: MessageType, source: Source[ByteString, _])(implicit
      hc: HeaderCarrier
    ): EitherT[Future, PresentationError, Source[ByteString, _]] =
      for {
        _ <- validationService.validateJson(messageType, source).asPresentation
        _ = auditService.audit(messageType.auditType, source, MimeTypes.JSON)
        converted <- conversionService.jsonToXml(messageType, source).asPresentation
      } yield converted

    def handleXml(departureId: MovementId, eoriNumber: EORINumber, messageType: MessageType, src: Source[ByteString, _])(implicit
      hc: HeaderCarrier
    ): EitherT[Future, PresentationError, UpdateMovementResponse] =
      withReusableSource(src) {
        source =>
          for {
            _              <- validationService.validateXml(messageType, source).asPresentation(jsonToXmlValidationErrorConverter, materializerExecutionContext)
            updateResponse <- departuresService.updateDeparture(departureId, messageType, source).asPresentation
            _              <- routerService.send(messageType, eoriNumber, departureId, updateResponse.messageId, source).asPresentation
          } yield updateResponse
      }

    (authActionNewEnrolmentOnly andThen messageSizeAction()).streamWithAwait {
      awaitFileWrite => implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          messageType    <- jsonParsingService.extractMessageType(request.body, MessageType.updateMessageTypesSentByDepartureTrader).asPresentation
          _              <- awaitFileWrite
          converted      <- handleJson(messageType, request.body)
          updateResponse <- handleXml(id, request.eoriNumber, messageType, converted)
        } yield updateResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          updateResponse => Accepted(Json.toJson(HateoasMovementUpdateResponse(id, updateResponse.messageId, MovementType.Departure)))
        )
    }
  }

  private def updateAndSendDeparture(departureId: MovementId, messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[Source[ByteString, _]]
  ) =
    for {
      declarationResult <- departuresService.updateDeparture(departureId, messageType, source).asPresentation
      _ <- routerService
        .send(messageType, request.eoriNumber, departureId, declarationResult.messageId, source)
        .asPresentation
    } yield declarationResult
}
