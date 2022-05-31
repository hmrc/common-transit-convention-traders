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
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.Logging
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
import v2.controllers.actions.MessageSizeAction
import v2.controllers.request.AuthenticatedRequest
import v2.controllers.stream.StreamingParsers
import v2.models.request.MessageType
import v2.models.responses.hateoas.HateoasDepartureDeclarationResponse
import v2.services.DeparturesService
import v2.services.ValidationService

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
  messageSizeAction: MessageSizeAction[AuthenticatedRequest]
)(implicit val materializer: Materializer)
    extends BaseController
    with V2DeparturesController
    with Logging
    with StreamingParsers
    with VersionedRouting
    with ErrorTranslator
    with TemporaryFiles {

  def submitDeclaration(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction).async(streamFromMemory) {
      implicit request =>
        withTemporaryFile {
          (temporaryFile, source) =>
            logger.info("Version 2 of endpoint has been called")

            implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

            (for {
              _ <- validationService.validateXML(MessageType.DepartureDeclaration, source).asPresentation

              fileSource = FileIO.fromPath(temporaryFile)

              declarationResult <- departuresService.saveDeclaration(request.eoriNumber, fileSource).asPresentation
            } yield declarationResult).fold[Result](
              presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
              result => Accepted(HateoasDepartureDeclarationResponse(result.departureId, result.messageId, MessageType.DepartureDeclaration))
            )
        }.toResult
    }

}
