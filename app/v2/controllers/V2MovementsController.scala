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
import uk.gov.hmrc.http
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.controllers.actions.providers.AcceptHeaderActionProvider
import v2.controllers.request.AuthenticatedRequest
import v2.controllers.stream.StreamingParsers
import v2.models._
import v2.models.errors.PersistenceError
import v2.models.errors.PresentationError
import v2.models.errors.PushNotificationError
import v2.models.request.MessageType
import v2.models.request.MessageUpdate
import v2.models.responses._
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

  def getMessageIds(
    movementType: MovementType,
    movementId: MovementId,
    receivedSince: Option[OffsetDateTime] = None,
    page: Option[PageNumber],
    count: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime]
  ): Action[AnyContent]

  def getMovement(movementType: MovementType, movementId: MovementId): Action[AnyContent]

  def getMovements(
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber],
    page: Option[PageNumber],
    count: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime],
    localReferenceNumber: Option[LocalReferenceNumber]
  ): Action[AnyContent]
  def attachMessage(movementType: MovementType, movementId: MovementId): Action[Source[ByteString, _]]
  def getMessageBody(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent]

  def attachMessageFromUpscan(
    eori: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    clientId: Option[ClientId]
  ): Action[UpscanResponse]
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
  upscanService: UpscanService,
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

  private lazy val jsonAndXmlAcceptHeaders = Seq(
    VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON,
    VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_XML
  )

  private lazy val jsonAndJsonWrappedXmlAcceptHeaders = Seq(
    VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON,
    VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML,
    VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN
  )

  private def contentSizeIsLessThanLimit(size: Long): EitherT[Future, PresentationError, Unit] = EitherT {
    if (size <= config.smallMessageSizeLimit) Future.successful(Right(()))
    else {
      Future.successful(Left(PresentationError.entityTooLargeError("Request Entity Too Large")))
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
          boxResponseOption <- mapToOptionalResponse(
            pushNotificationsService.associate(movementResponse.movementId, movementType, request.headers, request.eoriNumber)
          )
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
            MimeTypes.JSON,
            0L
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
          messageBody <- getBody(request.eoriNumber, movementType, movementId, messageId, messageSummary.body)
          body        <- mergeMessageSummaryAndBody(movementId, messageSummary, movementType, acceptHeader, messageBody)
        } yield body).fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Ok.chunked(response, Some(MimeTypes.JSON))
        )
    }

  private def getBody(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId, bodyOption: Option[Payload])(implicit
    hc: HeaderCarrier
  ): EitherT[Future, PresentationError, Option[BodyAndSize]] =
    bodyOption match {
      case Some(XmlPayload(value)) =>
        val byteString = ByteString(value, StandardCharsets.UTF_8)
        EitherT.rightT(Option(BodyAndSize(byteString.size, Source.single(byteString))))
      case None =>
        persistenceService
          .getMessageBody(eori, movementType, movementId, messageId)
          .map(Option(_))
          .leftFlatMap[Option[Source[ByteString, _]], PersistenceError] {
            case _: PersistenceError.MessageNotFound => EitherT.rightT[Future, PersistenceError](None)
            case e                                   => EitherT.leftT[Future, Option[Source[ByteString, _]]](e)
          }
          .asPresentation
          .flatMap[PresentationError, Option[BodyAndSize]] {
            case Some(x) => getFile(x).map(Option.apply)
            case None    => EitherT[Future, PresentationError, Option[BodyAndSize]](Future.successful(Right[PresentationError, Option[BodyAndSize]](None)))
          }
      case _ => EitherT.leftT(PresentationError.internalServiceError("Did not expect JsonPayload when getting message body"))
    }

  private def getFile(source: Source[ByteString, _]): EitherT[Future, PresentationError, BodyAndSize] =
    EitherT {
      Future
        .fromTry(Try(temporaryFileCreator.create()))
        .flatMap {
          file =>
            for {
              _    <- source.runWith(FileIO.toPath(file))
              size <- Future.fromTry(Try(Files.size(file)))
            } yield Right(BodyAndSize(size, FileIO.fromPath(file)))
        }
        .recover {
          case NonFatal(ex) =>
            Left(PresentationError.internalServiceError(cause = Some(ex)))
        }
    }

  private def formatMessageBody(
    messageSummary: MessageSummary,
    acceptHeader: String,
    bodyAndSizeMaybe: Option[BodyAndSize]
  )(implicit
    hc: HeaderCarrier
  ): EitherT[Future, PresentationError, BodyAndContentType] = {
    def bodyExists(bodyAndSize: BodyAndSize): EitherT[Future, PresentationError, BodyAndContentType] =
      acceptHeader match {
        case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON if bodyAndSize.size > config.smallMessageSizeLimit =>
          EitherT.leftT[Future, BodyAndContentType](
            PresentationError.notAcceptableError(s"Messages larger than ${config.smallMessageSizeLimit} bytes cannot be retrieved in JSON")
          )
        case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
          for {
            jsonStream <- conversionService.xmlToJson(messageSummary.messageType.get, bodyAndSize.body).asPresentation
            bodyWithContentType = BodyAndContentType(MimeTypes.JSON, jsonStream)
          } yield bodyWithContentType
        case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_XML =>
          EitherT.rightT[Future, PresentationError](
            BodyAndContentType(MimeTypes.XML, bodyAndSize.body)
          )
      }

    bodyAndSizeMaybe match {
      case Some(value) => bodyExists(value)
      case None =>
        EitherT.leftT[Future, BodyAndContentType](PresentationError.notFoundError(s"Body for message id ${messageSummary.id.value} does not exist"))
    }
  }

  private def mergeMessageSummaryAndBody(
    movementId: MovementId,
    messageSummary: MessageSummary,
    movementType: MovementType,
    acceptHeader: String,
    bodyAndSizeMaybe: Option[BodyAndSize]
  )(implicit
    hc: HeaderCarrier
  ): EitherT[Future, PresentationError, Source[ByteString, _]] = {
    def bodyExists(bodyAndSize: BodyAndSize): EitherT[Future, PresentationError, Source[ByteString, _]] =
      acceptHeader match {
        case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON if bodyAndSize.size > config.smallMessageSizeLimit =>
          EitherT.leftT[Future, Source[ByteString, _]](PresentationError.notAcceptableError("Large messages cannot be returned as json"))
        case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
          for {
            jsonStream <- conversionService.xmlToJson(messageSummary.messageType.get, bodyAndSize.body).asPresentation
            summary = messageSummary.copy(body = None)
            jsonHateoasResponse = Json
              .toJson(
                HateoasMovementMessageResponse(movementId, summary.id, summary, movementType)
              )
              .as[JsObject]
            stream <- jsonToByteStringStream(jsonHateoasResponse.fields, "body", jsonStream)
          } yield stream
        case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML | VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN =>
          if (messageSummary.body.isDefined)
            EitherT.rightT[Future, PresentationError](
              Source.single(ByteString(Json.stringify(HateoasMovementMessageResponse(movementId, messageSummary.id, messageSummary, movementType))))
            )
          else
            mergeStreamIntoJson(
              Json.toJson(HateoasMovementMessageResponse(movementId, messageSummary.id, messageSummary, movementType)).as[JsObject].fields,
              "body",
              bodyAndSize.body
            )
      }

    bodyAndSizeMaybe match {
      case Some(value) => bodyExists(value)
      case None =>
        EitherT.rightT[Future, PresentationError](
          Source.single(ByteString(Json.stringify(HateoasMovementMessageResponse(movementId, messageSummary.id, messageSummary, movementType))))
        )
    }
  }

  def getMessageBody(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonAndXmlAcceptHeaders)).async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          messageSummary <- persistenceService.getMessage(request.eoriNumber, movementType, movementId, messageId).asPresentation
          acceptHeader = request.headers.get(HeaderNames.ACCEPT).get
          messageBody <- getBody(request.eoriNumber, movementType, movementId, messageId, messageSummary.body)
          body        <- formatMessageBody(messageSummary, acceptHeader, messageBody)
        } yield body).fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Ok.chunked(response.body, Some(response.contentType))
        )
    }

  def getMessageIds(
    movementType: MovementType,
    movementId: MovementId,
    receivedSince: Option[OffsetDateTime],
    page: Option[PageNumber],
    count: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime]
  ): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- ensurePositive(page.map(_.value), "page")
          _ <- ensurePositive(count.map(_.value), "count")
          perPageCount = determineMaxPerPageCount(count.map(_.value))
          response <- persistenceService
            .getMessages(request.eoriNumber, movementType, movementId, receivedSince, page, perPageCount, receivedUntil)
            .asPresentation
        } yield response).fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Ok(Json.toJson(HateoasMovementMessageIdsResponse(movementId, response, receivedSince, movementType, page, count, receivedUntil)))
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

  def ensurePositive(parameter: Option[Long], keyName: String): EitherT[Future, PresentationError, Unit] =
    parameter match {
      case Some(x) if x <= 0 =>
        val error = PresentationError.badRequestError(s"The $keyName parameter must be a positive number")
        EitherT.leftT(error)
      case _ =>
        EitherT.rightT(())
    }

  def determineMaxPerPageCount(parameter: Option[Long]): Option[ItemCount] =
    parameter match {
      case Some(x) if x <= config.maxItemsPerPage => Some(ItemCount(x))
      case Some(x) if x > config.maxItemsPerPage  => Some(ItemCount(config.maxItemsPerPage))
      case None                                   => None
    }

  def getMovements(
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber],
    page: Option[PageNumber],
    count: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime],
    localReferenceNumber: Option[LocalReferenceNumber]
  ): Action[AnyContent] =
    authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader) async {
      request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- ensurePositive(page.map(_.value), "page")
          _ <- ensurePositive(count.map(_.value), "count")
          perPageCount = determineMaxPerPageCount(count.map(_.value))
          response <- persistenceService
            .getMovements(
              request.eoriNumber,
              movementType,
              updatedSince,
              movementEORI,
              movementReferenceNumber,
              page,
              perPageCount,
              receivedUntil,
              localReferenceNumber
            )
            .asPresentation
        } yield response).fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response =>
            Ok(
              Json.toJson(
                HateoasMovementIdsResponse(
                  response,
                  movementType,
                  updatedSince,
                  movementEORI,
                  movementReferenceNumber,
                  page,
                  count,
                  receivedUntil,
                  localReferenceNumber
                )
              )
            )
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
          _                      <- persistenceService.getMovement(request.eoriNumber, movementType, movementId).asPresentation
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
            MimeTypes.JSON,
            0L
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
      withReusableSourceAndSize(src) {
        (source, size) =>
          for {
            _              <- contentSizeIsLessThanLimit(size)
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

  def attachMessageFromUpscan(
    eori: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    clientId: Option[ClientId]
  ): Action[UpscanResponse] =
    Action.async(parse.json[UpscanResponse](UpscanResponse.upscanResponseReads)) {
      implicit request =>
        val originalHc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        // If the client ID is provided, ensure we add it to the headers.
        val clientIdHeader = clientId
          .map[Seq[(String, String)]](
            id => Seq(XClientIdHeader -> id.value)
          )
          .getOrElse(Seq.empty)

        // We add it to otherHeaders as hc.headers(...) doesn't check extraHeaders, and mirrors what would happen via
        // a direct call to the API Gateway.
        implicit val hc: http.HeaderCarrier = originalHc.copy(otherHeaders = clientIdHeader ++ originalHc.otherHeaders)

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
              MimeTypes.JSON,
              0L
            )

            logger.warn(s"""Upscan failed to process trader-uploaded file
                 |
                 |Movement ID: ${movementId.value}
                 |Message ID: ${messageId.value}
                 |
                 |Upscan Reference: ${reference.value}
                 |Reason: ${failureDetails.failureReason}
                 |Message: ${failureDetails.message}""".stripMargin)
            persistenceService.updateMessage(eori, movementType, movementId, messageId, MessageUpdate(MessageStatus.Failed, None, None))
            pushNotificationsService
              .postPpnsNotification(movementId, messageId, Json.toJson(PresentationError.badRequestError("Uploaded file not accepted.")))
            Future.successful(Ok)
          case UpscanSuccessResponse(_, downloadUrl, uploadDetails) =>
            def completeSmallMessage(): EitherT[Future, PushNotificationError, Unit] = {
              persistenceService.updateMessage(eori, movementType, movementId, messageId, MessageUpdate(MessageStatus.Success, None, None))
              pushNotificationsService.postPpnsNotification(
                movementId,
                messageId,
                Json.toJson(
                  Json.obj(
                    "code" -> "SUCCESS",
                    "message" ->
                      s"The message ${messageId.value} for movement ${movementId.value} was successfully processed"
                  )
                )
              )
            }

            // Download file to stream
            upscanService
              .upscanGetFile(downloadUrl) // TODO: If this fails, maybe consider returning 400 to upscan?
              .asPresentation
              .flatMap {
                withReusableSource[SubmissionRoute](_) {
                  source =>
                    val allowedTypes =
                      if (movementType == MovementType.Arrival) MessageType.messageTypesSentByArrivalTrader else MessageType.messageTypesSentByDepartureTrader
                    for {
                      // Extract type
                      messageType <- xmlParsingService.extractMessageType(source, allowedTypes).asPresentation
                      // Audit as soon as we can
                      _ = auditService.audit(messageType.auditType, source, MimeTypes.XML, uploadDetails.size)
                      // Validate file
                      _ <- validationService.validateXml(messageType, source).asPresentation
                      // Save file (this will check the size and put it in the right place.
                      _ <- persistenceService.updateMessageBody(messageType, eori, movementType, movementId, messageId, source).asPresentation
                      // Send message to router to be sent
                      submissionResult <- routerService.send(messageType, eori, movementId, messageId, source).asPresentation
                    } yield submissionResult
                }
              }
              .map {
                case SubmissionRoute.ViaEIS =>
                  completeSmallMessage()
                  Ok
                case SubmissionRoute.ViaSDES =>
                  Ok
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
    withReusableSourceAndSize(src) {
      (source, size) =>
        for {
          _      <- contentSizeIsLessThanLimit(size)
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
        pushNotificationsService.associate(movementResponse.movementId, movementType, request.headers, request.eoriNumber)
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
