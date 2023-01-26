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

package v2.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
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
import v2.models.errors.PushNotificationError
import v2.models.errors.UpscanInitiateError
import v2.models.request.MessageType
import v2.models.request.UpscanInitiate
import v2.models.responses.BoxResponse
import v2.models.responses.MovementResponse
import v2.models.responses.UpdateMovementResponse
import v2.models.responses.UpscanInitiateResponse
import v2.models.responses.hateoas._
import v2.services._
import v2.utils.PreMaterialisedFutureProvider

import java.time.OffsetDateTime
import scala.concurrent.Future

@ImplementedBy(classOf[V2MovementsControllerImpl])
trait V2MovementsController {
  def createMovement(movementType: MovementType): Action[Source[ByteString, _]]
  def getMessage(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent]
  def getMessageIds(movementType: MovementType, movementId: MovementId, receivedSince: Option[OffsetDateTime] = None): Action[AnyContent]
  def getMovement(movementType: MovementType, movementId: MovementId): Action[AnyContent]
  def getMovements(movementType: MovementType, updatedSince: Option[OffsetDateTime], movementEORI: Option[EORINumber]): Action[AnyContent]
  def attachMessage(movementType: MovementType, movementId: MovementId): Action[Source[ByteString, _]]
}

@Singleton
class V2MovementsControllerImpl @Inject() (
  val controllerComponents: ControllerComponents,
  authActionNewEnrolmentOnly: AuthNewEnrolmentOnlyAction,
  validationService: ValidationService,
  conversionService: ConversionService,
  movementsService: MovementsService,
  routerService: RouterService,
  auditService: AuditingService,
  pushNotificationsService: PushNotificationsService,
  messageSizeAction: MessageSizeActionProvider,
  acceptHeaderActionProvider: AcceptHeaderActionProvider,
  val metrics: Metrics,
  xmlParsingService: XmlMessageParsingService,
  jsonParsingService: JsonMessageParsingService,
  responseFormatterService: ResponseFormatterService,
  upscanService: UpscanService,
  val preMaterialisedFutureProvider: PreMaterialisedFutureProvider
)(implicit val materializer: Materializer, val temporaryFileCreator: TemporaryFileCreator)
    extends BaseController
    with V2MovementsController
    with Logging
    with StreamingParsers
    with VersionedRouting
    with ErrorTranslator
    with ContentTypeRouting
    with HasActionMetrics {

  lazy val sCounter: Counter = counter(s"success-counter")
  lazy val fCounter: Counter = counter(s"failure-counter")

  def createMovement(movementType: MovementType): Action[Source[ByteString, _]] =
    movementType match {
      case MovementType.Arrival =>
        contentTypeRoute {
          case Some(MimeTypes.XML)  => submitArrivalNotificationXML()
          case Some(MimeTypes.JSON) => submitArrivalNotificationJSON()
        }
      case MovementType.Departure =>
        contentTypeRoute {
          case Some(MimeTypes.XML)  => submitDepartureDeclarationXML()
          case Some(MimeTypes.JSON) => submitDepartureDeclarationJSON()
          case None                 => submitDepartureDeclarationLargeXML()
        }
    }

  private def submitDepartureDeclarationXML(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- validationService.validateXml(MessageType.DeclarationData, request.body).asPresentation
          _ = auditService.audit(AuditType.DeclarationData, request.body, MimeTypes.XML)
          movementResponse <- persistAndSendToEIS(request.body, MovementType.Departure, MessageType.DeclarationData)
        } yield movementResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Accepted(HateoasNewMovementResponse(response.movementId, response.boxResponse, None, MovementType.Departure))
        )
    }

  private def submitDepartureDeclarationLargeXML(): Action[Source[ByteString, _]] =
    authActionNewEnrolmentOnly.async(streamFromMemory) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        request.body.runWith(Sink.ignore)

        (for {
          upscan <- upscanService.upscanInitiate().asPresentation
          // _ = auditService.audit(AuditType.DeclarationData, request.body, MimeTypes.XML) // TODO - what data we need to send for auditing
          movementResponse <- persistAndLinkToBox(None, MovementType.Departure)
        } yield movementResponse.copy(upscanInitiateResponse = Some(upscan))).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Accepted(HateoasNewMovementResponse(response.movementId, response.boxResponse, response.upscanInitiateResponse, MovementType.Departure))
        )
    }

  private def submitDepartureDeclarationJSON(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- validationService.validateJson(MessageType.DeclarationData, request.body).asPresentation
          _ = auditService.audit(AuditType.DeclarationData, request.body, MimeTypes.JSON)
          xmlSource         <- conversionService.jsonToXml(MessageType.DeclarationData, request.body).asPresentation
          declarationResult <- validatePersistAndSendToEIS(xmlSource, MovementType.Departure, MessageType.DeclarationData)
        } yield declarationResult).fold[Result](
          presentationError => {
            fCounter.inc()
            Status(presentationError.code.statusCode)(Json.toJson(presentationError))
          },
          result => {
            sCounter.inc()
            Accepted(HateoasNewMovementResponse(result.movementId, result.boxResponse, None, MovementType.Departure))
          }
        )
    }

  private def submitArrivalNotificationXML(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- validationService.validateXml(MessageType.ArrivalNotification, request.body).asPresentation
          _ = auditService.audit(AuditType.ArrivalNotification, request.body, MimeTypes.XML)
          movementResponse <- persistAndSendToEIS(request.body, MovementType.Arrival, MessageType.ArrivalNotification)
        } yield movementResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Accepted(HateoasNewMovementResponse(response.movementId, response.boxResponse, None, MovementType.Arrival))
        )
    }

  private def submitArrivalNotificationJSON(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- validationService.validateJson(MessageType.ArrivalNotification, request.body).asPresentation
          _ = auditService.audit(AuditType.ArrivalNotification, request.body, MimeTypes.JSON)
          xmlSource     <- conversionService.jsonToXml(MessageType.ArrivalNotification, request.body).asPresentation
          arrivalResult <- validatePersistAndSendToEIS(xmlSource, MovementType.Arrival, MessageType.ArrivalNotification)
        } yield arrivalResult).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          result => Accepted(HateoasNewMovementResponse(result.movementId, result.boxResponse, None, MovementType.Arrival))
        )
    }

  def getMessage(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider()).async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          messageSummary          <- movementsService.getMessage(request.eoriNumber, movementType, movementId, messageId).asPresentation
          formattedMessageSummary <- responseFormatterService.formatMessageSummary(messageSummary, request.headers.get(HeaderNames.ACCEPT).get)
        } yield formattedMessageSummary).fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Ok(Json.toJson(HateoasMovementMessageResponse(movementId, messageId, response, movementType)))
        )
    }

  def getMessageIds(movementType: MovementType, movementId: MovementId, receivedSince: Option[OffsetDateTime]): Action[AnyContent] =
    authActionNewEnrolmentOnly.async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        movementsService
          .getMessages(request.eoriNumber, movementType, movementId, receivedSince)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasMovementMessageIdsResponse(movementId, response, receivedSince, movementType)))
          )
    }

  def getMovement(movementType: MovementType, movementId: MovementId): Action[AnyContent] =
    authActionNewEnrolmentOnly.async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        movementsService
          .getMovement(request.eoriNumber, movementType, movementId)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasMovementResponse(movementId, response, movementType)))
          )
    }

  def getMovements(movementType: MovementType, updatedSince: Option[OffsetDateTime], movementEORI: Option[EORINumber]): Action[AnyContent] =
    authActionNewEnrolmentOnly.async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        movementsService
          .getMovements(request.eoriNumber, movementType, updatedSince, movementEORI)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasMovementIdsResponse(response, movementType, updatedSince, movementEORI)))
          )
    }

  def attachMessage(movementType: MovementType, movementId: MovementId): Action[Source[ByteString, _]] =
    contentTypeRoute {
      case Some(MimeTypes.XML)  => attachMessageXML(movementId, movementType)
      case Some(MimeTypes.JSON) => attachMessageJSON(movementId, movementType)
    }

  private def attachMessageXML(movementId: MovementId, movementType: MovementType): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).streamWithAwait {
      awaitFileWrite => implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        val messageTypeList =
          if (movementType == MovementType.Arrival) MessageType.updateMessageTypesSentByArrivalTrader else MessageType.updateMessageTypesSentByDepartureTrader

        (for {
          messageType <- xmlParsingService.extractMessageType(request.body, messageTypeList).asPresentation
          _           <- awaitFileWrite
          _           <- validationService.validateXml(messageType, request.body).asPresentation
          _ = auditService.audit(messageType.auditType, request.body, MimeTypes.XML)
          updateMovementResponse <- updateAndSendToEIS(movementId, movementType, messageType, request.body)
        } yield updateMovementResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Accepted(Json.toJson(HateoasMovementUpdateResponse(movementId, response.messageId, movementType)))
        )
    }

  private def attachMessageJSON(id: MovementId, movementType: MovementType): Action[Source[ByteString, _]] = {

    def handleXml(movementId: MovementId, messageType: MessageType, src: Source[ByteString, _])(implicit
      hc: HeaderCarrier,
      request: AuthenticatedRequest[Source[ByteString, _]]
    ): EitherT[Future, PresentationError, UpdateMovementResponse] =
      withReusableSource(src) {
        source =>
          for {
            _              <- validationService.validateXml(messageType, source).asPresentation(jsonToXmlValidationErrorConverter, materializerExecutionContext)
            updateResponse <- updateAndSendToEIS(movementId, movementType, messageType, source)
          } yield updateResponse
      }

    (authActionNewEnrolmentOnly andThen messageSizeAction()).streamWithAwait {
      awaitFileWrite => implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        val messageTypeList =
          if (movementType == MovementType.Arrival) MessageType.updateMessageTypesSentByArrivalTrader else MessageType.updateMessageTypesSentByDepartureTrader

        (for {
          messageType <- jsonParsingService.extractMessageType(request.body, messageTypeList).asPresentation
          _           <- awaitFileWrite
          _           <- validationService.validateJson(messageType, request.body).asPresentation
          _ = auditService.audit(messageType.auditType, request.body, MimeTypes.JSON)
          converted      <- conversionService.jsonToXml(messageType, request.body).asPresentation
          updateResponse <- handleXml(id, messageType, converted)
        } yield updateResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          updateResponse => Accepted(Json.toJson(HateoasMovementUpdateResponse(id, updateResponse.messageId, movementType)))
        )
    }
  }

  private def updateAndSendToEIS(movementId: MovementId, movementType: MovementType, messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[_]
  ) =
    for {
      updateMovementResponse <- movementsService.updateMovement(movementId, movementType, messageType, source).asPresentation
      _ = pushNotificationsService.update(movementId)
      _ <- routerService
        .send(messageType, request.eoriNumber, movementId, updateMovementResponse.messageId, source)
        .asPresentation
    } yield updateMovementResponse

  private def validatePersistAndSendToEIS(
    src: Source[ByteString, _],
    movementType: MovementType,
    messageType: MessageType
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[Source[ByteString, _]]): EitherT[Future, PresentationError, MovementResponse] =
    withReusableSource(src) {
      source =>
        for {
          _      <- validationService.validateXml(messageType, source).asPresentation(jsonToXmlValidationErrorConverter, materializerExecutionContext)
          result <- persistAndSendToEIS(source, movementType, messageType)
        } yield result
    }

  private def persistAndSendToEIS(
    source: Source[ByteString, _],
    movementType: MovementType,
    messageType: MessageType
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[Source[ByteString, _]]) =
    for {
      movementResponse <- movementsService.createMovement(request.eoriNumber, movementType, Some(source)).asPresentation
      boxResponse      <- mapToBoxResponse(pushNotificationsService.associate(movementResponse.movementId, movementType, request.headers))
      _ <- routerService
        .send(
          messageType,
          request.eoriNumber,
          movementResponse.movementId,
          movementResponse.messageId.get,
          source
        ) // TODO- shall we assume we always get messageId for small messages
        .asPresentation
    } yield MovementResponse(movementResponse.movementId, movementResponse.messageId, boxResponse)

  private def persistAndLinkToBox(source: Option[Source[ByteString, _]], movementType: MovementType)(implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[Source[ByteString, _]]
  ) =
    for {
      movementResponse <- movementsService.createMovement(request.eoriNumber, movementType, source).asPresentation
      boxResponse      <- mapToBoxResponse(pushNotificationsService.associate(movementResponse.movementId, movementType, request.headers))
    } yield MovementResponse(movementResponse.movementId, None, boxResponse, None)

  private def mapToBoxResponse(boxResponse: EitherT[Future, PushNotificationError, BoxResponse]): EitherT[Future, PresentationError, Option[BoxResponse]] =
    EitherT[Future, PresentationError, Option[BoxResponse]] {
      boxResponse.fold(
        _ => Right(None),
        r => Right(Some(r))
      )
    }

}
