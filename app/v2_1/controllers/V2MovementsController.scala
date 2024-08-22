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

package v2_1.controllers

import cats.data.EitherT
import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import config.AppConfig
import config.Constants.XClientIdHeader
import controllers.common.AuthenticatedRequest
import controllers.common.ContentTypeRouting
import controllers.common.stream.StreamingParsers
import metrics.HasActionMetrics
import models.common.ClientId
import models.common.EORINumber
import models.common.ItemCount
import models.common.LocalReferenceNumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementReferenceNumber
import models.common.MovementType
import models.common.PageNumber
import models.common.errors.PersistenceError
import models.common.errors.PresentationError
import models.common.errors.PushNotificationError
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc._
import routing.VERSION_2_1_ACCEPT_HEADER_VALUE_JSON
import routing.VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML
import routing.VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN
import routing.VERSION_2_1_ACCEPT_HEADER_VALUE_XML
import routing.VERSION_2_ACCEPT_HEADER_VALUE_JSON
import routing.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML
import routing.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN
import routing.VERSION_2_ACCEPT_HEADER_VALUE_XML
import routing.VersionedRouting
import uk.gov.hmrc.http
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import v2_1.controllers.actions.AuthNewEnrolmentOnlyAction
import v2_1.controllers.actions.providers.AcceptHeaderActionProvider
import v2_1.models.AuditType._
import v2_1.models._
import v2_1.models.request.MessageType
import v2_1.models.request.MessageUpdate
import v2_1.models.responses._
import v2_1.models.responses.hateoas._
import v2_1.services._
import v2_1.utils.StreamWithFile

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import scala.concurrent.Future

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
  val metrics: MetricRegistry,
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

  private lazy val jsonOnlyAcceptHeader = Seq(VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value)

  private lazy val jsonAndXmlAcceptHeaders = Seq(
    VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value,
    VERSION_2_1_ACCEPT_HEADER_VALUE_XML.value
  )

  private lazy val jsonAndJsonWrappedXmlAcceptHeaders = Seq(
    VERSION_2_1_ACCEPT_HEADER_VALUE_JSON.value,
    VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML.value,
    VERSION_2_1_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN.value
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
          case ContentTypeRouting.ContentType.XML  => submitArrivalNotificationXML()
          case ContentTypeRouting.ContentType.JSON => submitArrivalNotificationJSON()
          case ContentTypeRouting.ContentType.None => submitLargeMessageXML(MovementType.Arrival)
        }
      case MovementType.Departure =>
        contentTypeRoute {
          case ContentTypeRouting.ContentType.XML  => submitDepartureDeclarationXML()
          case ContentTypeRouting.ContentType.JSON => submitDepartureDeclarationJSON()
          case ContentTypeRouting.ContentType.None => submitLargeMessageXML(MovementType.Departure)
        }
    }

  private def submitDepartureDeclarationXML(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async(streamFromMemory) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          source <- reUsableSourceRequest(request)
          size   <- calculateSize(source.head)
          _      <- contentSizeIsLessThanLimit(size)
          _ <- validationService.validateXml(MessageType.DeclarationData, source.lift(1).get).asPresentation.leftMap {
            err =>
              auditService.auditStatusEvent(
                ValidationFailed,
                Some(Json.toJson(err)),
                None,
                None,
                Some(request.eoriNumber),
                Some(MovementType.Departure),
                Some(MessageType.DeclarationData)
              )
              err
          }
          hateoasResponse <- persistAndSendToEIS(source.lift(2).get, MovementType.Departure, MessageType.DeclarationData, size, MimeTypes.XML)
        } yield hateoasResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          hateoasResponse => Accepted(hateoasResponse)
        )
    }

  private def submitDepartureDeclarationJSON(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async(streamFromMemory) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          source <- reUsableSourceRequest(request)
          size   <- calculateSize(source.head)
          _      <- contentSizeIsLessThanLimit(size)
          _ <- validationService.validateJson(MessageType.DeclarationData, source.lift(1).get).asPresentation.leftMap {
            err =>
              auditService.auditStatusEvent(
                ValidationFailed,
                Some(Json.toJson(err)),
                None,
                None,
                Some(request.eoriNumber),
                Some(MovementType.Departure),
                Some(MessageType.DeclarationData)
              )
              err
          }
          xmlSource       <- conversionService.jsonToXml(MessageType.DeclarationData, source.lift(2).get).asPresentation
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
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async(streamFromMemory) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          source <- reUsableSourceRequest(request)
          size   <- calculateSize(source.head)
          _      <- contentSizeIsLessThanLimit(size)
          _ <- validationService.validateXml(MessageType.ArrivalNotification, source.lift(1).get).asPresentation.leftMap {
            err =>
              auditService.auditStatusEvent(
                ValidationFailed,
                Some(Json.toJson(err)),
                None,
                None,
                Some(request.eoriNumber),
                Some(MovementType.Arrival),
                Some(MessageType.ArrivalNotification)
              )
              err
          }
          hateoasResponse <- persistAndSendToEIS(source.lift(2).get, MovementType.Arrival, MessageType.ArrivalNotification, size, MimeTypes.XML)
        } yield hateoasResponse).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          hateoasResponse => Accepted(hateoasResponse)
        )
    }

  private def submitArrivalNotificationJSON(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async(streamFromMemory) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          source <- reUsableSourceRequest(request)
          size   <- calculateSize(source.head)
          _      <- contentSizeIsLessThanLimit(size)
          _ <- validationService.validateJson(MessageType.ArrivalNotification, source.lift(1).get).asPresentation.leftMap {
            err =>
              auditService.auditStatusEvent(
                ValidationFailed,
                Some(Json.toJson(err)),
                None,
                None,
                Some(request.eoriNumber),
                Some(MovementType.Arrival),
                Some(MessageType.ArrivalNotification)
              )
              err
          }
          xmlSource       <- conversionService.jsonToXml(MessageType.ArrivalNotification, source.lift(2).get).asPresentation
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
          movementResponse <- persistenceService.createMovement(request.eoriNumber, movementType, None).asPresentation.leftMap {
            err =>
              auditService.auditStatusEvent(
                CreateMovementDBFailed,
                Some(Json.toJson(err)),
                None,
                None,
                Some(request.eoriNumber),
                Some(movementType),
                None
              )
              err
          }
          upscanResponse <- upscanService
            .upscanInitiate(request.eoriNumber, movementType, movementResponse.movementId, movementResponse.messageId)
            .asPresentation
          boxResponseOption <- mapToOptionalResponse(
            pushNotificationsService
              .associate(movementResponse.movementId, movementType, request.headers, request.eoriNumber)
              .leftMap {
                err =>
                  val auditType = if (err == PushNotificationError.BoxNotFound) PushPullNotificationGetBoxFailed else PushNotificationFailed
                  auditService.auditStatusEvent(
                    auditType,
                    Some(Json.obj("message" -> err.toString)),
                    Some(movementResponse.movementId),
                    Some(movementResponse.messageId),
                    Some(request.eoriNumber),
                    Some(movementType),
                    None
                  )
                  err
              }
          )

          _ = auditService.auditStatusEvent(
            AuditType.LargeMessageSubmissionRequested,
            None,
            Some(movementResponse.movementId),
            Some(movementResponse.messageId),
            Some(request.eoriNumber),
            Some(movementType),
            None
          )
        } yield HateoasNewMovementResponse(movementResponse.movementId, movementResponse.messageId, boxResponseOption, Some(upscanResponse), movementType))
          .fold[Result](
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Accepted(response)
          )
    }

  def getMessage(movementType: MovementType, movementId: MovementId, messageId: MessageId): Action[AnyContent] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonAndJsonWrappedXmlAcceptHeaders)).async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        (for {
          messageSummary <- persistenceService.getMessage(request.eoriNumber, movementType, movementId, messageId).asPresentation.leftMap {
            err =>
              auditService.auditStatusEvent(
                GetMovementMessageDBFailed,
                Some(Json.toJson(err)),
                Some(movementId),
                Some(messageId),
                Some(request.eoriNumber),
                Some(movementType),
                None
              )
              err
          }
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
        EitherT.rightT(Option(BodyAndSize(byteString.size.toLong, Source.single(byteString))))
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
    for {
      sources <- reUsableSource(source, 2)
      size    <- calculateSize(sources.lift(0).get)
    } yield BodyAndSize(size, sources.lift(1).get)

  private def formatMessageBody(
    messageSummary: MessageSummary,
    acceptHeader: String,
    bodyAndSizeMaybe: Option[BodyAndSize]
  )(implicit
    hc: HeaderCarrier
  ): EitherT[Future, PresentationError, BodyAndContentType] = {
    def bodyExists(bodyAndSize: BodyAndSize): EitherT[Future, PresentationError, BodyAndContentType] =
      VersionedRouting.formatAccept(acceptHeader) match {
        case Left(error) => EitherT.leftT(error)
        case Right(value) =>
          value match {
            case VERSION_2_ACCEPT_HEADER_VALUE_JSON if bodyAndSize.size > config.smallMessageSizeLimit =>
              EitherT.leftT[Future, BodyAndContentType](
                PresentationError.notAcceptableError(s"Messages larger than ${config.smallMessageSizeLimit} bytes cannot be retrieved in JSON")
              )
            case VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
              for {
                jsonStream <- conversionService.xmlToJson(messageSummary.messageType.get, bodyAndSize.body).asPresentation
                bodyWithContentType = BodyAndContentType(MimeTypes.JSON, jsonStream)
              } yield bodyWithContentType
            case VERSION_2_ACCEPT_HEADER_VALUE_XML =>
              EitherT.rightT[Future, PresentationError](
                BodyAndContentType(MimeTypes.XML, bodyAndSize.body)
              )
            case _ =>
              EitherT.leftT(PresentationError.notAcceptableError("Invalid accept header"))
          }
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
      VersionedRouting.formatAccept(acceptHeader) match {
        case Left(error) => EitherT.leftT(error)
        case Right(value) =>
          value match {
            case VERSION_2_ACCEPT_HEADER_VALUE_JSON if bodyAndSize.size > config.smallMessageSizeLimit =>
              EitherT.leftT[Future, Source[ByteString, _]](PresentationError.notAcceptableError("Large messages cannot be returned as json"))
            case VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
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
            case VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML | VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN =>
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
            case _ =>
              EitherT.leftT(PresentationError.notAcceptableError("Invalid accept header"))
          }
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
        if (config.enablePhase5) {
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
        } else {
          val presentationError = PresentationError.notAcceptableError(
            "CTC Traders API version 2 is not yet available. Please continue to use version 1 to submit transit messages."
          )
          Future.successful(Status(presentationError.code.statusCode)(Json.toJson(presentationError)))
        }
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
            .leftMap {
              err =>
                auditService.auditStatusEvent(
                  GetMovementMessagesDBFailed,
                  Some(Json.toJson(err)),
                  Some(movementId),
                  None,
                  Some(request.eoriNumber),
                  Some(movementType),
                  None
                )
                err
            }
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
          .leftMap {
            err =>
              auditService.auditStatusEvent(
                GetMovementDBFailed,
                Some(Json.toJson(err)),
                Some(movementId),
                None,
                Some(request.eoriNumber),
                Some(movementType),
                None
              )
              err
          }
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

  private def determineMaxPerPageCount(parameter: Option[Long]): ItemCount =
    parameter
      .map(
        count => ItemCount(Math.min(config.maxItemsPerPage.toLong, count))
      )
      .getOrElse(ItemCount(config.defaultItemsPerPage.toLong))

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
            .leftMap {
              err =>
                auditService.auditStatusEvent(
                  GetMovementsDBFailed,
                  Some(Json.toJson(err)),
                  None,
                  None,
                  Some(request.eoriNumber),
                  Some(movementType),
                  None
                )
                err
            }
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
      case ContentTypeRouting.ContentType.XML  => attachMessageXML(movementId, movementType)
      case ContentTypeRouting.ContentType.JSON => attachMessageJSON(movementId, movementType)
      case ContentTypeRouting.ContentType.None => initiateLargeMessage(movementId, movementType)
    }

  private def initiateLargeMessage(movementId: MovementId, movementType: MovementType): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async(streamFromMemory) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        request.body.runWith(Sink.ignore)

        (for {
          _ <- persistenceService.getMovement(request.eoriNumber, movementType, movementId).asPresentation.leftMap {
            err =>
              if (err.code.statusCode == NOT_FOUND)
                auditService.auditStatusEvent(
                  CustomerRequestedMissingMovement,
                  Some(Json.toJson(err)),
                  Some(movementId),
                  None,
                  Some(request.eoriNumber),
                  Some(movementType),
                  None
                )
              err
          }
          updateMovementResponse <- persistenceService.addMessage(movementId, movementType, None, None).asPresentation.leftMap {
            err =>
              auditService.auditStatusEvent(
                AddMessageDBFailed,
                Some(Json.toJson(err)),
                Some(movementId),
                None,
                Some(request.eoriNumber),
                Some(movementType),
                None
              )
              err
          }

          _ = auditService.auditStatusEvent(
            AuditType.LargeMessageSubmissionRequested,
            None,
            Some(movementId),
            Some(updateMovementResponse.messageId),
            Some(request.eoriNumber),
            Some(movementType),
            None
          )

          upscanResponse <- upscanService
            .upscanInitiate(request.eoriNumber, movementType, movementId, updateMovementResponse.messageId)
            .asPresentation
        } yield HateoasMovementUpdateResponse(movementId, updateMovementResponse.messageId, movementType, Some(upscanResponse))).fold[Result](
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          response => Accepted(response)
        )
    }

  private def attachMessageXML(movementId: MovementId, movementType: MovementType): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async(streamFromMemory) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        val messageTypeList =
          if (movementType == MovementType.Arrival) MessageType.updateMessageTypesSentByArrivalTrader else MessageType.updateMessageTypesSentByDepartureTrader

        (for {
          source      <- reUsableSourceRequest(request)
          size        <- calculateSize(source.head)
          _           <- contentSizeIsLessThanLimit(size)
          messageType <- xmlParsingService.extractMessageType(source.lift(1).get, messageTypeList).asPresentation
          _ <- validationService.validateXml(messageType, source.lift(2).get).asPresentation.leftMap {
            err =>
              auditService.auditStatusEvent(
                ValidationFailed,
                Some(Json.toJson(err)),
                Some(movementId),
                None,
                Some(request.eoriNumber),
                Some(messageType.movementType),
                Some(messageType)
              )
              err
          }
          updateMovementResponse <- updateAndSendToEIS(movementId, movementType, messageType, source.lift(3).get, size, MimeTypes.XML)
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
      for {
        source <- reUsableSource(src)
        size   <- calculateSize(source.head)
        _      <- contentSizeIsLessThanLimit(size)
        _ <- validationService
          .validateXml(messageType, source.lift(1).get)
          .asPresentation(jsonToXmlValidationErrorConverter, materializerExecutionContext)
          .leftMap {
            err =>
              auditService.auditStatusEvent(
                ValidationFailed,
                Some(Json.toJson(err)),
                Some(movementId),
                None,
                Some(request.eoriNumber),
                Some(messageType.movementType),
                Some(messageType)
              )
              err
          }
        updateResponse <- updateAndSendToEIS(movementId, movementType, messageType, source.lift(2).get, size, MimeTypes.XML)
      } yield updateResponse

    (authActionNewEnrolmentOnly andThen acceptHeaderActionProvider(jsonOnlyAcceptHeader)).async(streamFromMemory) {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        val messageTypeList =
          if (movementType == MovementType.Arrival) MessageType.updateMessageTypesSentByArrivalTrader else MessageType.updateMessageTypesSentByDepartureTrader

        (for {
          source      <- reUsableSourceRequest(request)
          size        <- calculateSize(source.head)
          _           <- contentSizeIsLessThanLimit(size)
          messageType <- jsonParsingService.extractMessageType(source.lift(1).get, messageTypeList).asPresentation
          _ <- validationService.validateJson(messageType, source.lift(2).get).asPresentation.leftMap {
            err =>
              auditService.auditStatusEvent(
                ValidationFailed,
                Some(Json.toJson(err)),
                Some(id),
                None,
                Some(request.eoriNumber),
                Some(movementType),
                Some(messageType)
              )
              err
          }
          converted      <- conversionService.jsonToXml(messageType, source.lift(3).get).asPresentation
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
            auditService.auditStatusEvent(
              AuditType.TraderFailedUpload,
              Some(Json.toJson(failureDetails)),
              Some(movementId),
              Some(messageId),
              Some(eori),
              Some(movementType),
              None
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
              .postPpnsNotification(movementId, messageId, Json.toJson(PresentationError.badRequestError(failureDetails.message)))
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
                      // Validate file
                      _ <- validationService.validateXml(messageType, source).asPresentation.leftMap {
                        err =>
                          auditService.auditStatusEvent(
                            ValidationFailed,
                            Some(Json.toJson(err)),
                            Some(movementId),
                            Some(messageId),
                            Some(eori),
                            Some(movementType),
                            Some(messageType)
                          )
                          err
                      }
                      // Save file (this will check the size and put it in the right place.
                      _ <- persistenceService.updateMessageBody(messageType, eori, movementType, movementId, messageId, source).asPresentation
                      _ = auditService.auditMessageEvent(
                        messageType.auditType,
                        MimeTypes.XML,
                        uploadDetails.size,
                        source,
                        Some(movementId),
                        Some(messageId),
                        Some(eori),
                        Some(movementType),
                        Some(messageType)
                      )

                      // Send message to router to be sent
                      submissionResult <- routerService.send(messageType, eori, movementId, messageId, source).asPresentation.leftMap {
                        err =>
                          auditService.auditStatusEvent(
                            SubmitAttachMessageFailed,
                            Some(Json.toJson(err)),
                            Some(movementId),
                            Some(messageId),
                            Some(eori),
                            Some(movementType),
                            Some(messageType)
                          )
                          err
                      }
                      _ = auditService.auditStatusEvent(
                        TraderToNCTSSubmissionSuccessful,
                        None,
                        Some(movementId),
                        Some(messageId),
                        Some(eori),
                        Some(movementType),
                        Some(messageType)
                      )
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

  private def updateAndSendToEIS(
    movementId: MovementId,
    movementType: MovementType,
    messageType: MessageType,
    source: Source[ByteString, _],
    size: Long,
    contentType: String
  )(implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[_]
  ) =
    for {
      sources <- reUsableSource(source)
      updateMovementResponse <- persistenceService.addMessage(movementId, movementType, Some(messageType), Some(sources.head)).asPresentation.leftMap {
        err =>
          val auditType = if (err.code.statusCode == NOT_FOUND) CustomerRequestedMissingMovement else AddMessageDBFailed
          auditService.auditStatusEvent(
            auditType,
            Some(Json.toJson(err)),
            Some(movementId),
            None,
            Some(request.eoriNumber),
            Some(movementType),
            Some(messageType)
          )
          err
      }
      _ = auditService.auditMessageEvent(
        messageType.auditType,
        contentType,
        size,
        sources.lift(1).get,
        Some(movementId),
        Some(updateMovementResponse.messageId),
        Some(request.eoriNumber),
        Some(movementType),
        Some(messageType)
      )
      _ = pushNotificationsService.update(movementId).leftMap {
        err =>
          auditService.auditStatusEvent(
            PushNotificationUpdateFailed,
            Some(Json.obj("message" -> err.toString)),
            Some(movementId),
            Some(updateMovementResponse.messageId),
            Some(request.eoriNumber),
            Some(movementType),
            Some(messageType)
          )
          err
      }
      _ <- routerService
        .send(messageType, request.eoriNumber, movementId, updateMovementResponse.messageId, sources.lift(2).get)
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
            auditService.auditStatusEvent(
              SubmitAttachMessageFailed,
              Some(Json.toJson(err)),
              Some(movementId),
              Some(updateMovementResponse.messageId),
              Some(request.eoriNumber),
              Some(movementType),
              Some(messageType)
            )

            err
        }
      _ = auditService.auditStatusEvent(
        TraderToNCTSSubmissionSuccessful,
        None,
        Some(movementId),
        Some(updateMovementResponse.messageId),
        Some(request.eoriNumber),
        Some(movementType),
        Some(messageType)
      )
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
    for {
      source <- reUsableSource(src)
      size   <- calculateSize(source.head)
      _      <- contentSizeIsLessThanLimit(size)
      _ <- validationService
        .validateXml(messageType, source.lift(1).get)
        .asPresentation(jsonToXmlValidationErrorConverter, materializerExecutionContext)
        .leftMap {
          err =>
            auditService.auditStatusEvent(
              ValidationFailed,
              Some(Json.toJson(err)),
              None,
              None,
              Some(request.eoriNumber),
              Some(movementType),
              Some(messageType)
            )
            err
        }
      result <- persistAndSendToEIS(source.lift(2).get, movementType, messageType, size, MimeTypes.XML)
    } yield result

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
    messageType: MessageType,
    size: Long,
    contentType: String
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[Source[ByteString, _]]) =
    for {
      sources <- reUsableSource(source)
      movementResponse <- persistenceService.createMovement(request.eoriNumber, movementType, Some(sources.head)).asPresentation.leftMap {
        err =>
          auditService.auditStatusEvent(
            CreateMovementDBFailed,
            Some(Json.toJson(err)),
            None,
            None,
            Some(request.eoriNumber),
            Some(movementType),
            Some(messageType)
          )
          err
      }
      _ = auditService.auditMessageEvent(
        messageType.auditType,
        contentType,
        size,
        sources.lift(1).get,
        Some(movementResponse.movementId),
        Some(movementResponse.messageId),
        Some(request.eoriNumber),
        Some(movementType),
        Some(messageType)
      )
      boxResponseOption <- mapToOptionalResponse[PushNotificationError, BoxResponse](
        pushNotificationsService
          .associate(movementResponse.movementId, movementType, request.headers, request.eoriNumber)
          .leftMap {
            err =>
              val auditType = if (err == PushNotificationError.BoxNotFound) PushPullNotificationGetBoxFailed else PushNotificationFailed
              auditService.auditStatusEvent(
                auditType,
                Some(Json.obj("message" -> err.toString)),
                Some(movementResponse.movementId),
                Some(movementResponse.messageId),
                Some(request.eoriNumber),
                Some(movementType),
                None
              )
              err
          }
      )
      _ <- routerService
        .send(
          messageType,
          request.eoriNumber,
          movementResponse.movementId,
          movementResponse.messageId,
          sources.lift(2).get
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
            auditService.auditStatusEvent(
              if (movementType == MovementType.Departure) SubmitDeclarationFailed else SubmitArrivalNotificationFailed,
              Some(Json.toJson(err)),
              Some(movementResponse.movementId),
              Some(movementResponse.messageId),
              Some(request.eoriNumber),
              Some(movementType),
              Some(messageType)
            )
            err
        }
      _ = auditService.auditStatusEvent(
        TraderToNCTSSubmissionSuccessful,
        None,
        Some(movementResponse.movementId),
        Some(movementResponse.messageId),
        Some(request.eoriNumber),
        Some(movementType),
        Some(messageType)
      )
      _ <- updateSmallMessageStatus(
        request.eoriNumber,
        movementType,
        movementResponse.movementId,
        movementResponse.messageId,
        MessageStatus.Success
      ).asPresentation

    } yield HateoasNewMovementResponse(movementResponse.movementId, movementResponse.messageId, boxResponseOption, None, movementType)

  private def mapToOptionalResponse[E, R](
    eitherT: EitherT[Future, E, R]
  ): EitherT[Future, PresentationError, Option[R]] =
    EitherT[Future, PresentationError, Option[R]] {
      eitherT.fold(
        _ => Right(None),
        r => Right(Some(r))
      )
    }

  private def materializeSource(source: Source[ByteString, _]): EitherT[Future, PresentationError, Seq[ByteString]] =
    EitherT(
      source
        .runWith(Sink.seq)
        .map(Right(_): Either[PresentationError, Seq[ByteString]])
        .recover {
          error =>
            Left(PresentationError.internalServiceError(cause = Some(error)))
        }
    )

  // Function to create a new source from the materialized sequence
  private def createReusableSource(seq: Seq[ByteString]): Source[ByteString, _] = Source(seq.toList)

  private def reUsableSourceRequest(request: Request[Source[ByteString, _]]): EitherT[Future, PresentationError, List[Source[ByteString, _]]] = for {
    byteStringSeq <- materializeSource(request.body)
  } yield List.fill(4)(createReusableSource(byteStringSeq))

  private def reUsableSource(source: Source[ByteString, _], numberOfSources: Int = 4): EitherT[Future, PresentationError, List[Source[ByteString, _]]] = for {
    byteStringSeq <- materializeSource(source)
  } yield List.fill(numberOfSources)(createReusableSource(byteStringSeq))

  // Function to calculate the size using EitherT
  private def calculateSize(source: Source[ByteString, _]): EitherT[Future, PresentationError, Long] = {
    val sizeFuture: Future[Either[PresentationError, Long]] = source
      .map(_.size.toLong)
      .runWith(Sink.fold(0L)(_ + _))
      .map(
        size => Right(size): Either[PresentationError, Long]
      )
      .recover {
        case _: Exception => Left(PresentationError.internalServiceError())
      }

    EitherT(sizeFuture)
  }

}
