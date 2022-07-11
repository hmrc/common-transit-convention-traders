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
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.Logging
import play.api.http.HeaderNames
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
import v2.controllers.request.AuthenticatedRequest
import v2.controllers.stream.StreamingParsers
import v2.models.errors.PresentationError
import v2.models.request.MessageType
import v2.models.responses.hateoas.HateoasDepartureDeclarationResponse
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
  messageSizeAction: MessageSizeActionProvider
)(implicit val materializer: Materializer)
    extends BaseController
    with V2DeparturesController
    with Logging
    with StreamingParsers
    with VersionedRouting
    with ErrorTranslator
    with TemporaryFiles {

  def submitDeclaration(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction()).async(streamFromMemory) {
      implicit request =>
        logger.info("Version 2 of endpoint has been called")

        request.headers.get(HeaderNames.CONTENT_TYPE) match {
          case Some(MimeTypes.XML)  => submitDeclarationXML
          case Some(MimeTypes.JSON) => submitDeclarationJSON
          case contentType          => handleInvalidContentType(contentType)
        }

    }

  def submitDeclarationJSON(implicit request: AuthenticatedRequest[Source[ByteString, _]]): Future[Result] = {
    request.body.to(Sink.ignore).run()
    Future.successful(Accepted)
  }

  def submitDeclarationXML(implicit request: AuthenticatedRequest[Source[ByteString, _]]): Future[Result] =
    withTemporaryFile {
      (temporaryFile, source) =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        (for {
          _ <- validationService.validateXML(MessageType.DepartureDeclaration, source).asPresentation

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

  def handleInvalidContentType(contentType: Option[String])(implicit request: AuthenticatedRequest[Source[ByteString, _]]): Future[Result] = {
    request.body.to(Sink.ignore).run()
    Future.successful(
      UnsupportedMediaType(
        Json.toJson(
          PresentationError.unsupportedMediaTypeError(
            contentType
              .map(
                ct => s"Content-Type $ct is not supported!"
              )
              .getOrElse("A Content-Type header is required!")
          )
        )
      )
    )
  }

}
