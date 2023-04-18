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
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.codahale.metrics.Counter
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import config.Constants.XClientIdHeader
import metrics.HasActionMetrics
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc._
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.controllers.actions.providers.AcceptHeaderActionProvider
import v2.controllers.request.AuthenticatedRequest
import v2.controllers.stream.StreamingParsers
import v2.models._
import v2.models.errors.PresentationError
import v2.models.errors.PushNotificationError
import v2.models.request.MessageType
import v2.models.request.MessageUpdate
import v2.models.responses.BoxResponse
import v2.models.responses.LargeMessageAuditRequest
import v2.models.responses.MessageSummary
import v2.models.responses.TraderFailedUploadAuditRequest
import v2.models.responses.UpdateMovementResponse
import v2.models.responses.UpscanFailedResponse
import v2.models.responses.UpscanResponse
import v2.models.responses.UpscanSuccessResponse
import v2.models.responses.hateoas._
import v2.services._
import v2.utils.StreamWithFile

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.OffsetDateTime
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

@ImplementedBy(classOf[V2MovementsControllerImpl])
trait V2MovementsController {
  def createMovement(movementType: MovementType): Action[Source[ByteString, _]]
  def getMessage(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent]
  def getMessageIds(movementType: MovementType, movementId: MovementId, receivedSince: Option[OffsetDateTime] = None): Action[AnyContent]
  def getMovement(movementType: MovementType, movementId: MovementId): Action[AnyContent]
  def getMovements(movementType: MovementType, updatedSince: Option[OffsetDateTime], movementEORI: Option[EORINumber]): Action[AnyContent]
  def attachMessage(movementType: MovementType, movementId: MovementId): Action[Source[ByteString, _]]
  def getMessageBody(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent]
  def attachMessageFromUpscan(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[UpscanResponse]
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
  acceptHeaderActionProvider: AcceptHeaderActionProvider,
  val metrics: Metrics,
  xmlParsingService: XmlMessageParsingService,
  jsonParsingService: JsonMessageParsingService,
  responseFormatterService: ResponseFormatterService,
  upscanService: UpscanService,
  objectStoreService: ObjectStoreService,
  config: AppConfig
)(implicit val materializer: Materializer, val temporaryFileCreator: TemporaryFileCreator)
    extends BaseController
    with V2MovementsController
    with Logging
    with StreamingParsers
    with StreamWithFile
    with VersionedRouting
    with ConvertError
    with ContentTypeRouting
    with HasActionMetrics {

  private lazy val sCounter: Counter = counter(s"success-counter")
  private lazy val fCounter: Counter = counter(s"failure-counter")

  private lazy val jsonOnlyAcceptHeader = Seq(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)
  private lazy val xmlOnlyAcceptHeader  = Seq(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_XML)

  private lazy val jsonAndJsonWrappedXmlAcceptHeaders = Seq(
    VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON,
    VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML,
    VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN
  )

  private def contentSizeIsLessThanLimit(size: Long): EitherT[Future, PresentationError, Unit] = EitherT {
    if (size <= config.smallMessageSizeLimit) Future.successful(Right(()))
    else {
      Future.successful(Left(PresentationError.entityTooLargeError(s"Your message size must be less than ${config.smallMessageSizeLimit} bytes")))
    }
  }

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
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).streamWithSize {
      implicit request => size =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          _ <- contentSizeIsLessThanLimit(size)
          _ <- validationService.validateXml(MessageType.DeclarationData, request.body).asPresentation
          _ = auditService.audit(AuditType.DeclarationData, request.body, MimeTypes.XML, size)
          hateoasResponse <- persistAndSendToEIS(request.body, MovementType.Departure, MessageType.DeclarationData)
        } yield hateoasResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          hateoasResponse => Accepted(hateoasResponse)
        )
    }

  private def submitDepartureDeclarationJSON(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).streamWithSize {
      implicit request => size =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- contentSizeIsLessThanLimit(size)
          _ <- validationService.validateJson(MessageType.DeclarationData, request.body).asPresentation
          _ = auditService.audit(AuditType.DeclarationData, request.body, MimeTypes.JSON, size)
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
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).streamWithSize {
      implicit request => size =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          _ <- contentSizeIsLessThanLimit(size)
          _ <- validationService.validateXml(MessageType.ArrivalNotification, request.body).asPresentation
          _ = auditService.audit(AuditType.ArrivalNotification, request.body, MimeTypes.XML, size)
          hateoasResponse <- persistAndSendToEIS(request.body, MovementType.Arrival, MessageType.ArrivalNotification)
        } yield hateoasResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          hateoasResponse => Accepted(hateoasResponse)
        )
    }

  private def submitArrivalNotificationJSON(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).streamWithSize {
      implicit request => size =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- contentSizeIsLessThanLimit(size)
          _ <- validationService.validateJson(MessageType.ArrivalNotification, request.body).asPresentation
          _ = auditService.audit(AuditType.ArrivalNotification, request.body, MimeTypes.JSON, size)
          xmlSource       <- conversionService.jsonToXml(MessageType.ArrivalNotification, request.body).asPresentation
          hateoasResponse <- validatePersistAndSendToEIS(xmlSource, MovementType.Arrival, MessageType.ArrivalNotification)
        } yield hateoasResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          hateoasResponse => Accepted(hateoasResponse)
        )
    }

  private def submitLargeMessageXML(movementType: MovementType): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async(streamFromMemory) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        request.body.runWith(Sink.ignore)

        (for {
          movementResponse <- persistenceService.createMovement(request.eoriNumber, movementType, None).asPresentation
          upscanResponse <- upscanService
            .upscanInitiate(request.eoriNumber, movementType, movementResponse.movementId, movementResponse.messageId)
            .asPresentation
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
        } yield HateoasNewMovementResponse(movementResponse.movementId, boxResponseOption, Some(upscanResponse), movementType)).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Accepted(response)
        )
    }

  def getMessage(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonAndJsonWrappedXmlAcceptHeaders)).async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          messageSummary <- persistenceService.getMessage(request.eoriNumber, movementType, movementId, messageId).asPresentation
          acceptHeader = request.headers.get(HeaderNames.ACCEPT).get
          body <- messageSummary.uri match {
            case Some(uri) =>
              for {
                size <- getFileSize(uri.asResourceLocation.get)
                result <-
                  if (size <= config.smallMessageSizeLimit) processSmallMessage(movementId, movementType, messageSummary, acceptHeader)
                  else processLargeMessage(movementId, movementType, messageSummary, acceptHeader)
              } yield result
            case None => processSmallMessage(movementId, movementType, messageSummary, acceptHeader)
          }
        } yield body).fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Ok.chunked(response, Some(MimeTypes.JSON))
        )
    }

  private def getFileSize(resourceLocation: ObjectStoreResourceLocation)(implicit hc: HeaderCarrier) =
    objectStoreService.getMessage(resourceLocation).asPresentation.flatMap {
      source =>
        EitherT {
          Future
            .fromTry(Try(temporaryFileCreator.create()))
            .flatMap {
              file =>
                for {
                  _    <- source.runWith(FileIO.toPath(file))
                  size <- Future.fromTry(Try(Files.size(file)))
                  _    <- Future.fromTry(Try(file.delete()))
                } yield Right(size)
            }
            .recover {
              case NonFatal(ex) =>
                Left(PresentationError.internalServiceError(cause = Some(ex)))
            }
        }
    }

  private def objectStoreMessageAsJsonWrappedXml(movementId: MovementId, messageSummary: MessageSummary, movementType: MovementType)(implicit
    hc: HeaderCarrier
  ) =
    for {
      resourceLocation <- extractResourceLocation(messageSummary.uri.get)
      bodyStream       <- objectStoreService.getMessage(resourceLocation).asPresentation
      json: JsObject = Json.toJson(HateoasMovementMessageResponse(movementId, messageSummary.id, messageSummary, movementType)).as[JsObject]
      stream <- mergeStreamIntoJson(json.fields, "body", bodyStream)
    } yield stream

  private def processLargeMessage(movementId: MovementId, movementType: MovementType, messageSummary: MessageSummary, acceptHeader: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, PresentationError, Source[ByteString, _]] =
    acceptHeader match {
      case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
        EitherT.leftT[Future, Source[ByteString, _]](PresentationError.notAcceptableError("Large messages cannot be returned as json"))
      case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML | VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN =>
        objectStoreMessageAsJsonWrappedXml(movementId, messageSummary, movementType)
    }

  private def extractResourceLocation(objectStoreURI: ObjectStoreURI): EitherT[Future, PresentationError, ObjectStoreResourceLocation] =
    EitherT {
      Future.successful(
        objectStoreURI.asResourceLocation
          .toRight(PresentationError.badRequestError(s"Provided Object Store URI is not owned by ${ObjectStoreURI.expectedOwner}"))
      )
    }

  private def processSmallMessage(movementId: MovementId, movementType: MovementType, messageSummary: MessageSummary, acceptHeader: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, PresentationError, Source[ByteString, _]] =
    acceptHeader match {
      case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
        for {
          formattedMessageSummary <- responseFormatterService.formatMessageSummary(messageSummary, acceptHeader)
          jsonHateoasResponse = Json
            .toJson(
              HateoasMovementMessageResponse(movementId, formattedMessageSummary.id, formattedMessageSummary, movementType)
            )
            .as[JsObject]
          stream <- jsonToByteStringStream(jsonHateoasResponse.fields)
        } yield stream
      case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML | VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN =>
        messageSummary.uri.isDefined match {
          case true => objectStoreMessageAsJsonWrappedXml(movementId, messageSummary, movementType)
          case false =>
            val jsonHateoasResponse = Json
              .toJson(
                HateoasMovementMessageResponse(movementId, messageSummary.id, messageSummary, movementType)
              )
              .as[JsObject]
            jsonToByteStringStream(jsonHateoasResponse.fields)
        }
    }

  def getMessageBody(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(xmlOnlyAcceptHeader)).async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          messageSummary <- persistenceService.getMessage(request.eoriNumber, movementType, movementId, messageId).asPresentation
          body <- messageSummary match {
            case MessageSummary(_, _, _, Some(body), _, _) => stringToByteStringStream(body.value)
            case MessageSummary(_, _, _, _, _, Some(uri)) =>
              extractResourceLocation(uri).flatMap {
                resourceLocation =>
                  objectStoreService.getMessage(resourceLocation).asPresentation
              }
            case _ => EitherT.leftT[Future, Source[ByteString, _]](PresentationError.notFoundError(s"Body for message id ${messageId.value} does not exist"))
          }
        } yield body).fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Ok.chunked(response, Some(MimeTypes.XML))
        )
    }

  def getMessageIds(movementType: MovementType, movementId: MovementId, receivedSince: Option[OffsetDateTime]): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async {
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
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async {
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
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async {
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
      case None                 => initiateLargeMessage(movementId, movementType)
    }

  private def initiateLargeMessage(movementId: MovementId, movementType: MovementType): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async(streamFromMemory) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        request.body.runWith(Sink.ignore)

        (for {
          updateMovementResponse <- persistenceService.addMessage(movementId, movementType, None, None).asPresentation
          upscanResponse <- upscanService
            .upscanInitiate(request.eoriNumber, movementType, movementId, updateMovementResponse.messageId)
            .asPresentation
          auditResponse = Json.toJson(
            LargeMessageAuditRequest(
              movementId,
              updateMovementResponse.messageId,
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
        } yield HateoasMovementUpdateResponse(movementId, updateMovementResponse.messageId, movementType, Some(upscanResponse))).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Accepted(response)
        )
    }

  private def attachMessageXML(movementId: MovementId, movementType: MovementType): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).streamWithSize {
      implicit request => size =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        val messageTypeList =
          if (movementType == MovementType.Arrival) MessageType.updateMessageTypesSentByArrivalTrader else MessageType.updateMessageTypesSentByDepartureTrader

        (for {
          _           <- contentSizeIsLessThanLimit(size)
          messageType <- xmlParsingService.extractMessageType(request.body, messageTypeList).asPresentation
          _           <- validationService.validateXml(messageType, request.body).asPresentation
          _ = auditService.audit(messageType.auditType, request.body, MimeTypes.XML, size)
          updateMovementResponse <- updateAndSendToEIS(movementId, movementType, messageType, request.body)
        } yield updateMovementResponse).fold[Result](
          // update status to fail
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Accepted(Json.toJson(HateoasMovementUpdateResponse(movementId, response.messageId, movementType, None)))
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

    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).streamWithSize {
      implicit request => size =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        val messageTypeList =
          if (movementType == MovementType.Arrival) MessageType.updateMessageTypesSentByArrivalTrader else MessageType.updateMessageTypesSentByDepartureTrader

        (for {
          _           <- contentSizeIsLessThanLimit(size)
          messageType <- jsonParsingService.extractMessageType(request.body, messageTypeList).asPresentation
          _           <- validationService.validateJson(messageType, request.body).asPresentation
          _ = auditService.audit(messageType.auditType, request.body, MimeTypes.JSON, size)
          converted      <- conversionService.jsonToXml(messageType, request.body).asPresentation
          updateResponse <- handleXml(id, messageType, converted)
        } yield updateResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          updateResponse => Accepted(Json.toJson(HateoasMovementUpdateResponse(id, updateResponse.messageId, movementType, None)))
        )
    }
  }

  def attachMessageFromUpscan(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[UpscanResponse] =
    Action.async(parse.json[UpscanResponse](UpscanResponse.upscanResponseReads)) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        request.body match {
          case UpscanFailedResponse(reference, failureDetails) =>
            val auditReq = Json.toJson(
              TraderFailedUploadAuditRequest(
                movementId,
                messageId,
                eori,
                movementType
              )
            )
            auditService.audit(
              AuditType.TraderFailedUploadEvent,
              Source.single(ByteString(auditReq.toString(), StandardCharsets.UTF_8)),
              MimeTypes.JSON
            )

            logger.warn(s"""Upscan failed to process trader-uploaded file
                 |
                 |Movement ID: ${movementId.value}
                 |Message ID: ${messageId.value}
                 |
                 |Upscan Reference: ${reference.value}
                 |Reason: ${failureDetails.failureReason}
                 |Message: ${failureDetails.message}""".stripMargin)

            pushNotificationsService
              .postPpnsNotification(movementId, messageId, Json.toJson(PresentationError.badRequestError("Uploaded file not accepted")))
            Future.successful(Ok)
          case UpscanSuccessResponse(_, downloadUrl, uploadDetails) =>
            def routeLarge(messageType: MessageType): EitherT[Future, PresentationError, Unit] =
              for {
                // Get the message to see if we need to send the file via SDES (TODO: this will hopefully disappear if we get the router checking the size)
                message <- persistenceService.getMessage(eori, movementType, movementId, messageId).asPresentation
                uri <- EitherT(
                  Future.successful(message.uri.map(Right(_)).getOrElse(Left(PresentationError.internalServiceError("URI is not there as expected"))))
                )
                _ <- routerService.sendLargeMessage(messageType, eori, movementId, messageId, uri).asPresentation
              } yield ()

            def routeSmall(messageType: MessageType, source: Source[ByteString, _]): EitherT[Future, PresentationError, Unit] =
              for {
                _ <- routerService.send(messageType, eori, movementId, messageId, source).asPresentation
                // TODO: This should be in the router
                _ = persistenceService.updateMessage(eori, movementType, movementId, messageId, MessageUpdate(MessageStatus.Success, None, None))
                _ = pushNotificationsService.postPpnsNotification(
                  movementId,
                  messageId,
                  Json.toJson(HateoasMovementUpdateResponse(movementId, messageId, movementType, None))
                )
              } yield ()

            // Download file to stream
            upscanService
              .upscanGetFile(downloadUrl) // TODO: If this fails, maybe consider returning 400 to upscan?
              .asPresentation
              .flatMap {
                withReusableSource[Status](_) {
                  source =>
                    val allowedTypes =
                      if (movementType == MovementType.Arrival) MessageType.messageTypesSentByArrivalTrader else MessageType.messageTypesSentByDepartureTrader
                    (for {
                      // Extract type
                      messageType <- xmlParsingService.extractMessageType(source, allowedTypes).asPresentation
                      // Audit as soon as we can
                      _ = auditService.audit(messageType.auditType, source, MimeTypes.XML, uploadDetails.size)
                      // Validate file
                      _ <- validationService.validateXml(messageType, source).asPresentation
                      // Save file (this will check the size and put it in the right place.
                      _ <- persistenceService.updateMessageBody(messageType, eori, movementType, movementId, messageId, source).asPresentation
                      // Temporary: if large -- ideally this limit should be set by the router and ONLY by the router, and will do this in a future update (TODO)
                      // We'll also want to return if the file was submitted to EIS and ERMIS directly (OK), or via SDES (Accepted)
                      // as this will determine whether we send a success push notification message
                      isLarge = uploadDetails.size > config.smallMessageSizeLimit
                      // Route file (this will need the object store location for now)
                      _ <- if (isLarge) routeLarge(messageType) else routeSmall(messageType, source)
                    } yield Ok)
                }
              }
              .valueOr {
                presentationError =>
                  // we failed, so mark message as failure (but we can do that async)
                  persistenceService.updateMessage(eori, movementType, movementId, messageId, MessageUpdate(MessageStatus.Failed, None, None))
                  pushNotificationsService.postPpnsNotification(movementId, messageId, Json.toJson(presentationError))
                  Ok
              }
        }
    }

  private def updateAndSendToEIS(movementId: MovementId, movementType: MovementType, messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[_]
  ) =
    for {
      updateMovementResponse <- persistenceService.addMessage(movementId, movementType, Some(messageType), Some(source)).asPresentation
      _ = pushNotificationsService.update(movementId)
      _ <- routerService
        .send(messageType, request.eoriNumber, movementId, updateMovementResponse.messageId, source)
        .asPresentation
        .leftMap {
          err =>
            updateSmallMessageStatus(
              request.eoriNumber,
              movementType,
              movementId,
              updateMovementResponse.messageId,
              MessageStatus.Failed
            )
            err
        }
      _ <- updateSmallMessageStatus(
        request.eoriNumber,
        movementType,
        movementId,
        updateMovementResponse.messageId,
        MessageStatus.Success
      ).asPresentation
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

  private def updateSmallMessageStatus(
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    messageStatus: MessageStatus
  )(implicit
    hc: HeaderCarrier
  ) =
    persistenceService
      .updateMessage(
        eoriNumber,
        movementType,
        movementId,
        messageId,
        MessageUpdate(messageStatus, None, None)
      )

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
        .leftMap {
          err =>
            updateSmallMessageStatus(
              request.eoriNumber,
              movementType,
              movementResponse.movementId,
              movementResponse.messageId,
              MessageStatus.Failed
            )
            err
        }
      _ <- updateSmallMessageStatus(
        request.eoriNumber,
        movementType,
        movementResponse.movementId,
        movementResponse.messageId,
        MessageStatus.Success
      ).asPresentation
    } yield HateoasNewMovementResponse(movementResponse.movementId, boxResponseOption, None, movementType)

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
