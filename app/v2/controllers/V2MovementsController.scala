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
import config.Constants.XClientIdHeader
import metrics.HasActionMetrics
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.JsValue
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
import v2.models.MessageStatus
import v2.models.MovementId
import v2.models.MovementType
import v2.models.ObjectStoreURI
import v2.models.errors.PresentationError
import v2.models.errors.PushNotificationError
import v2.models.request.MessageType
import v2.models.request.MessageUpdate
import v2.models.responses.UpscanResponse.DownloadUrl
import v2.models.responses.BoxResponse
import v2.models.responses.LargeMessageAuditRequest
import v2.models.responses.UpdateMovementResponse
import v2.models.responses.UpscanResponse
import v2.models.responses.hateoas._
import v2.services._
import v2.utils.StreamWithFile

import java.nio.charset.StandardCharsets
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
  def attachLargeMessage(eori: EORINumber, movementId: MovementId, messageId: MessageId): Action[JsValue]
}

@Singleton
class V2MovementsControllerImpl @Inject() (
  val controllerComponents: ControllerComponents,
  authActionNewEnrolmentOnly: AuthNewEnrolmentOnlyAction,
  validationService: ValidationService,
  conversionService: ConversionService,
  persistenceService: PersistenceService,
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
  objectStoreService: ObjectStoreService
)(implicit val materializer: Materializer, val temporaryFileCreator: TemporaryFileCreator)
    extends BaseController
    with V2MovementsController
    with Logging
    with StreamingParsers
    with StreamWithFile
    with VersionedRouting
    with ConvertError
    with ContentTypeRouting
    with UpscanResponseParser
    with HasActionMetrics {

  lazy val sCounter: Counter = counter(s"success-counter")
  lazy val fCounter: Counter = counter(s"failure-counter")

  def createMovement(movementType: MovementType): Action[Source[ByteString, _]] =
    movementType match {
      case MovementType.Arrival =>
        contentTypeRoute {
          case Some(MimeTypes.XML)  => submitArrivalNotificationXML()
          case Some(MimeTypes.JSON) => submitArrivalNotificationJSON()
          case None                 => submitLargeMessageXML(MovementType.Arrival)
        }
      case MovementType.Departure =>
        contentTypeRoute {
          case Some(MimeTypes.XML)  => submitDepartureDeclarationXML()
          case Some(MimeTypes.JSON) => submitDepartureDeclarationJSON()
          case None                 => submitLargeMessageXML(MovementType.Departure)
        }
    }

  private def submitDepartureDeclarationXML(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(acceptOnlyJson = true) andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- validationService.validateXml(MessageType.DeclarationData, request.body).asPresentation
          _ = auditService.audit(AuditType.DeclarationData, request.body, MimeTypes.XML)
          hateoasResponse <- persistAndSendToEIS(request.body, MovementType.Departure, MessageType.DeclarationData)
        } yield hateoasResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          hateoasResponse => Accepted(hateoasResponse)
        )
    }

  private def submitDepartureDeclarationJSON(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(acceptOnlyJson = true) andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- validationService.validateJson(MessageType.DeclarationData, request.body).asPresentation
          _ = auditService.audit(AuditType.DeclarationData, request.body, MimeTypes.JSON)
          xmlSource       <- conversionService.jsonToXml(MessageType.DeclarationData, request.body).asPresentation
          hateoasResponse <- validatePersistAndSendToEIS(xmlSource, MovementType.Departure, MessageType.DeclarationData)
        } yield hateoasResponse).fold[Result](
          presentationError => {
            fCounter.inc()
            Status(presentationError.code.statusCode)(Json.toJson(presentationError))
          },
          hateoasResponse => {
            sCounter.inc()
            Accepted(hateoasResponse)
          }
        )
    }

  private def submitArrivalNotificationXML(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(acceptOnlyJson = true) andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- validationService.validateXml(MessageType.ArrivalNotification, request.body).asPresentation
          _ = auditService.audit(AuditType.ArrivalNotification, request.body, MimeTypes.XML)
          hateoasResponse <- persistAndSendToEIS(request.body, MovementType.Arrival, MessageType.ArrivalNotification)
        } yield hateoasResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          hateoasResponse => Accepted(hateoasResponse)
        )
    }

  private def submitArrivalNotificationJSON(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(acceptOnlyJson = true) andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- validationService.validateJson(MessageType.ArrivalNotification, request.body).asPresentation
          _ = auditService.audit(AuditType.ArrivalNotification, request.body, MimeTypes.JSON)
          xmlSource       <- conversionService.jsonToXml(MessageType.ArrivalNotification, request.body).asPresentation
          hateoasResponse <- validatePersistAndSendToEIS(xmlSource, MovementType.Arrival, MessageType.ArrivalNotification)
        } yield hateoasResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          hateoasResponse => Accepted(hateoasResponse)
        )
    }

  private def submitLargeMessageXML(movementType: MovementType): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(acceptOnlyJson = true)).async(streamFromMemory) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        request.body.runWith(Sink.ignore)

        (for {
          movementResponse  <- persistenceService.createMovement(request.eoriNumber, movementType, None).asPresentation
          upscanResponse    <- upscanService.upscanInitiate(request.eoriNumber, movementResponse.movementId, movementResponse.messageId).asPresentation
          boxResponseOption <- mapToOptionalResponse(pushNotificationsService.associate(movementResponse.movementId, movementType, request.headers))
          auditResponse = Json.toJson(
            LargeMessageAuditRequest(
              movementResponse.movementId,
              movementResponse.messageId,
              movementType,
              request.headers.get(XClientIdHeader),
              upscanResponse
            )
          )
          _ = auditService.audit(
            AuditType.LargeMessageSubmissionRequested,
            Source.single(ByteString(auditResponse.toString(), StandardCharsets.UTF_8)),
            MimeTypes.JSON
          )
        } yield HateoasNewMovementResponse(movementResponse, boxResponseOption, Some(upscanResponse), movementType)).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Accepted(response)
        )
    }

  def getMessage(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(acceptOnlyJson = false)).async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          messageSummary          <- persistenceService.getMessage(request.eoriNumber, movementType, movementId, messageId).asPresentation
          formattedMessageSummary <- responseFormatterService.formatMessageSummary(messageSummary, request.headers.get(HeaderNames.ACCEPT).get)
        } yield formattedMessageSummary).fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Ok(Json.toJson(HateoasMovementMessageResponse(movementId, messageId, response, movementType)))
        )
    }

  def getMessageIds(movementType: MovementType, movementId: MovementId, receivedSince: Option[OffsetDateTime]): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(acceptOnlyJson = true)).async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        persistenceService
          .getMessages(request.eoriNumber, movementType, movementId, receivedSince)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasMovementMessageIdsResponse(movementId, response, receivedSince, movementType)))
          )
    }

  def getMovement(movementType: MovementType, movementId: MovementId): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(acceptOnlyJson = true)).async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        persistenceService
          .getMovement(request.eoriNumber, movementType, movementId)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasMovementResponse(movementId, response, movementType)))
          )
    }

  def getMovements(movementType: MovementType, updatedSince: Option[OffsetDateTime], movementEORI: Option[EORINumber]): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(acceptOnlyJson = true)).async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        persistenceService
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
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(acceptOnlyJson = true) andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        val messageTypeList =
          if (movementType == MovementType.Arrival) MessageType.updateMessageTypesSentByArrivalTrader else MessageType.updateMessageTypesSentByDepartureTrader

        (for {
          messageType <- xmlParsingService.extractMessageType(request.body, messageTypeList).asPresentation
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

    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(acceptOnlyJson = true) andThen messageSizeAction()).stream {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        val messageTypeList =
          if (movementType == MovementType.Arrival) MessageType.updateMessageTypesSentByArrivalTrader else MessageType.updateMessageTypesSentByDepartureTrader

        (for {
          messageType <- jsonParsingService.extractMessageType(request.body, messageTypeList).asPresentation
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

  def attachLargeMessage(eori: EORINumber, movementId: MovementId, messageId: MessageId): Action[JsValue] =
    Action.async(parse.json) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        parseAndLogUpscanResponse(request.body) match {
          case Left(presentationError) => Future.successful(Status(presentationError.code.statusCode)(Json.toJson(presentationError)))
          case Right(upscanResponse) =>
            (for {
              downloadUrl   <- handleUpscanSuccessResponse(upscanResponse)
              objectSummary <- objectStoreService.addMessage(downloadUrl, movementId, messageId).asPresentation
              messageUpdate = MessageUpdate(MessageStatus.Processing, Some(ObjectStoreURI(objectSummary.location.asUri)))

              _ <- persistenceService.updateMessage(movementId, messageId, messageUpdate).asPresentation

              sendMessage <- routerService
                .sendLargeMessage(
                  MessageType.DeclarationData, // Only IE015 is supported as of now for large message
                  eori,
                  movementId,
                  messageId,
                  ObjectStoreURI(objectSummary.location.asUri)
                )
                .asPresentation
            } yield sendMessage).fold[Result](
              _ => Ok, //TODO: Send notification to PPNS with details of the error
              _ => Ok  //TODO: Send notification to PPNS with details of the success
            )
        }
    }

  private def handleUpscanSuccessResponse(upscanResponse: UpscanResponse): EitherT[Future, PresentationError, DownloadUrl] =
    EitherT {
      Future.successful(upscanResponse.downloadUrl.toRight {
        PresentationError.badRequestError("Upscan failed to process file")
      })
    }

  private def updateAndSendToEIS(movementId: MovementId, movementType: MovementType, messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[_]
  ) =
    for {
      updateMovementResponse <- persistenceService.addMessage(movementId, movementType, messageType, source).asPresentation
      _ = pushNotificationsService.update(movementId)
      _ <- routerService
        .send(messageType, request.eoriNumber, movementId, updateMovementResponse.messageId, source)
        .asPresentation
    } yield updateMovementResponse

  private def validatePersistAndSendToEIS(
    src: Source[ByteString, _],
    movementType: MovementType,
    messageType: MessageType
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[Source[ByteString, _]]) =
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
      movementResponse <- persistenceService.createMovement(request.eoriNumber, movementType, Some(source)).asPresentation
      boxResponseOption <- mapToOptionalResponse[PushNotificationError, BoxResponse](
        pushNotificationsService.associate(movementResponse.movementId, movementType, request.headers)
      )
      _ <- routerService
        .send(
          messageType,
          request.eoriNumber,
          movementResponse.movementId,
          movementResponse.messageId,
          source
        )
        .asPresentation
    } yield HateoasNewMovementResponse(movementResponse, boxResponseOption, None, movementType)

  private def mapToOptionalResponse[E, R](
    eitherT: EitherT[Future, E, R]
  ): EitherT[Future, PresentationError, Option[R]] =
    EitherT[Future, PresentationError, Option[R]] {
      eitherT.fold(
        _ => Right(None),
        r => Right(Some(r))
      )
    }

}
