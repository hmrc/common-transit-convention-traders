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

import akka.stream.IOResult
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import com.kenshoo.play.metrics.Metrics
import metrics.HasActionMetrics
import play.api.Logging
import play.api.http.MimeTypes
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import play.api.mvc.Result
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.controllers.actions.providers.MessageSizeActionProvider
import v2.controllers.request.AuthenticatedRequest
import v2.controllers.stream.StreamingParsers
import v2.models.AuditType
import v2.models.errors.PresentationError
import v2.models.MessageId
import v2.models.DepartureId
import v2.models.request.MessageType
import v2.models.responses.DeclarationResponse
import v2.models.responses.hateoas.HateoasDepartureDeclarationResponse
import v2.models.responses.hateoas.HateoasDepartureMessageResponse
import v2.models.responses.hateoas.HateoasDepartureResponse
import v2.services.AuditingService
import v2.services.ConversionService
import v2.services.DeparturesService
import v2.services.RouterService
import v2.services.ValidationService
import com.codahale.metrics.Counter
import v2.models.responses.hateoas.HateoasDepartureMessageIdsResponse

import java.time.OffsetDateTime
import scala.concurrent.Future

@ImplementedBy(classOf[V2DeparturesControllerImpl])
trait V2DeparturesController {
  def submitDeclaration(): Action[Source[ByteString, _]]
  def getMessage(departureId: DepartureId, messageId: MessageId): Action[AnyContent]
  def getMessageIds(departureId: DepartureId, receivedSince: Option[OffsetDateTime] = None): Action[AnyContent]
  def getDeparture(departureId: DepartureId): Action[AnyContent]
}

@Singleton
class V2DeparturesControllerImpl @Inject() (
  val controllerComponents: ControllerComponents,
  val temporaryFileCreator: TemporaryFileCreator,
  authActionNewEnrolmentOnly: AuthNewEnrolmentOnlyAction,
  validationService: ValidationService,
  conversionService: ConversionService,
  departuresService: DeparturesService,
  routerService: RouterService,
  auditService: AuditingService,
  messageSizeAction: MessageSizeActionProvider,
  val metrics: Metrics
)(implicit val materializer: Materializer)
    extends BaseController
    with V2DeparturesController
    with Logging
    with StreamingParsers
    with VersionedRouting
    with ErrorTranslator
    with TemporaryFiles
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
    (authActionNewEnrolmentOnly andThen messageSizeAction()).async(streamFromMemory) {
      implicit request =>
        withTemporaryFile {
          (temporaryFile, source) =>
            implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
            val messageType: MessageType   = MessageType.DepartureDeclaration
            val fileSource                 = FileIO.fromPath(temporaryFile)

            (for {
              _ <- validationService.validateJson(messageType, source).asPresentation
              _ = auditService.audit(AuditType.DeclarationData, fileSource, MimeTypes.JSON)

              xmlSource         <- conversionService.jsonToXml(messageType, fileSource).asPresentation
              declarationResult <- persistAndSendToEIS(xmlSource, messageType)
            } yield declarationResult).fold[Result](
              presentationError => {
                fCounter.inc()
                Status(presentationError.code.statusCode)(Json.toJson(presentationError))
              },
              result => {
                sCounter.inc()
                Accepted(HateoasDepartureDeclarationResponse(result.departureId))
              }
            )
        }.toResult
    }

  def persistAndSendToEIS(
    src: Source[ByteString, _],
    messageType: MessageType
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[Source[ByteString, _]]): EitherT[Future, PresentationError, DeclarationResponse] =
    withTemporaryFileA(
      src,
      (temporaryFile, xmlSource) => {
        val fileSource = FileIO.fromPath(temporaryFile)
        for {
          _      <- validationService.validateXml(messageType, xmlSource).asPresentation(jsonToXmlValidationErrorConverter, materializerExecutionContext)
          result <- persistAndSend(fileSource)
        } yield result
      }
    )

  def submitDeclarationXML(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).async(streamFromMemory) {
      implicit request =>
        withTemporaryFile {
          (temporaryFile, source) =>
            implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

            (for {
              _ <- validationService.validateXml(MessageType.DepartureDeclaration, source).asPresentation

              fileSource = FileIO.fromPath(temporaryFile)
              // TODO: See if we can parallelise this call with the one to persistence, below.
              // Note it's an =, not <-, as we don't care (here) for its response, once it's sent, it should be
              // non-blocking
              _ = auditService.audit(AuditType.DeclarationData, fileSource, MimeTypes.XML)

              declarationResult <- persistAndSend(fileSource)
            } yield declarationResult).fold[Result](
              presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
              result => Accepted(HateoasDepartureDeclarationResponse(result.departureId))
            )
        }.toResult
    }

  private def persistAndSend(
    fileSource: Source[ByteString, Future[IOResult]]
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[Source[ByteString, _]]) =
    for {
      declarationResult <- departuresService.saveDeclaration(request.eoriNumber, fileSource).asPresentation
      _ <- routerService
        .send(MessageType.DepartureDeclaration, request.eoriNumber, declarationResult.departureId, declarationResult.messageId, fileSource)
        .asPresentation
    } yield declarationResult

  def getMessage(departureId: DepartureId, messageId: MessageId): Action[AnyContent] =
    authActionNewEnrolmentOnly.async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        departuresService
          .getMessage(request.eoriNumber, departureId, messageId)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasDepartureMessageResponse(departureId, messageId, response)))
          )
    }

  def getMessageIds(departureId: DepartureId, receivedSince: Option[OffsetDateTime]): Action[AnyContent] =
    authActionNewEnrolmentOnly.async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        departuresService
          .getMessageIds(request.eoriNumber, departureId)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasDepartureMessageIdsResponse(departureId, response, receivedSince)))
          )
    }

  def getDeparture(departureId: DepartureId): Action[AnyContent] =
    authActionNewEnrolmentOnly.async {
      implicit request =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        departuresService
          .getDeparture(request.eoriNumber, departureId)
          .asPresentation
          .fold(
            presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
            response => Ok(Json.toJson(HateoasDepartureResponse(departureId, response)))
          )
    }

}
