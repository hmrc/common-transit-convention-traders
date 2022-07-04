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
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.Logging
import play.api.http.MimeTypes
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import play.api.mvc.Result
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.controllers.actions.providers.MessageSizeActionProvider
import v2.controllers.stream.StreamingParsers
import v2.models.AuditType
import v2.models.request.MessageType
import v2.models.responses.hateoas.HateoasDepartureDeclarationResponse
import v2.services.AuditingService
import v2.services.DeparturesService
import v2.services.RouterService
import v2.services.ValidationService

import scala.concurrent.Future

@ImplementedBy(classOf[V2DeparturesControllerImpl])
trait V2DeparturesController {

  def submitDeclaration(): Action[Source[ByteString, _]]

}

@Singleton
class V2DeparturesControllerImpl @Inject() (
  val controllerComponents: ControllerComponents,
  val temporaryFileCreator: TemporaryFileCreator,
  authActionNewEnrolmentOnly: AuthNewEnrolmentOnlyAction,
  validationService: ValidationService,
  departuresService: DeparturesService,
  routerService: RouterService,
  auditService: AuditingService,
  messageSizeAction: MessageSizeActionProvider
)(implicit val materializer: Materializer)
    extends BaseController
    with V2DeparturesController
    with Logging
    with StreamingParsers
    with VersionedRouting
    with ErrorTranslator
    with TemporaryFiles
    with ContentTypeRouting
    with SourceParallelisation {

  private def auditAndCallService[E, A](in: Source[ByteString, _], auditType: AuditType)(
    block: Source[ByteString, _] => EitherT[Future, E, A]
  )(implicit hc: HeaderCarrier, materializer: Materializer): EitherT[Future, E, A] =
    callInParallel(in) {
      source =>
        // fire and forget for auditing
        auditService.audit(auditType, source)
        block(source)
    }

  def submitDeclaration(): Action[Source[ByteString, _]] =
    contentTypeRoute {
      case Some(MimeTypes.XML)  => submitDeclarationXML()
      case Some(MimeTypes.JSON) => submitDeclarationJSON()
    }

  def submitDeclarationJSON(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).async(streamFromMemory) {
      implicit request =>
        request.body.to(Sink.ignore).run()
        Future.successful(Accepted)
    }

  def submitDeclarationXML(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).async(streamFromMemory) {
      implicit request =>
        withTemporaryFile {
          (temporaryFile, source) =>
            implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

            (for {
              _ <- auditAndCallService(source, AuditType.DeclarationData)(validationService.validateXML(MessageType.DepartureDeclaration, _).asPresentation)

              fileSource = FileIO.fromPath(temporaryFile)

              declarationResult <- departuresService.saveDeclaration(request.eoriNumber, fileSource).asPresentation
              _ <- routerService
                .send(MessageType.DepartureDeclaration, request.eoriNumber, declarationResult.departureId, declarationResult.messageId, fileSource)
                .asPresentation
            } yield declarationResult).fold[Result](
              presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
              result => Accepted(HateoasDepartureDeclarationResponse(result.departureId))
            )
        }.toResult
    }

}
