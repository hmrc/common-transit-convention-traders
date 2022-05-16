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
import play.api.libs.Files
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import play.api.mvc.Result
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.controllers.actions.MessageSizeAction
import v2.controllers.stream.StreamingParsers
import v2.models.errors.BaseError
import v2.models.errors.FailedToValidateError
import v2.models.errors.FailedToValidateError.InvalidMessageTypeError
import v2.models.errors.FailedToValidateError.OtherError
import v2.models.errors.FailedToValidateError.SchemaFailedToValidateError
import v2.models.request.MessageType
import v2.services.ValidationService

import scala.concurrent.Future
import scala.util.Try

@ImplementedBy(classOf[V2DeparturesControllerImpl])
trait V2DeparturesController {

  def submitDeclaration(): Action[Source[ByteString, _]]

}

@Singleton
class V2DeparturesControllerImpl @Inject() (
  val controllerComponents: ControllerComponents,
  temporaryFileCreator: TemporaryFileCreator,
  authActionNewEnrolmentOnly: AuthNewEnrolmentOnlyAction,
  validationService: ValidationService,
  messageSizeAction: MessageSizeAction
)(implicit val materializer: Materializer)
    extends BaseController
    with V2DeparturesController
    with Logging
    with StreamingParsers
    with VersionedRouting {

  def withTemporaryFile[A](
    onSucceed: Files.TemporaryFile => EitherT[Future, BaseError, A]
  )(implicit request: Request[Source[ByteString, _]]): EitherT[Future, BaseError, A] =
    EitherT(Future.successful(Try(temporaryFileCreator.create()).toEither))
      .leftMap {
        thr =>
          request.body.runWith(Sink.ignore)
          BaseError.internalServiceError(cause = Some(thr))
      }
      .flatMap {
        temporaryFile =>
          val result = onSucceed(temporaryFile)
          temporaryFile.delete()
          result
      }

  def translateValidationError(validationError: FailedToValidateError): BaseError = validationError match {
    case err: OtherError                               => BaseError.internalServiceError(cause = err.thr)
    case InvalidMessageTypeError(messageType)          => BaseError.badRequestError(s"$messageType is not a valid message type")
    case SchemaFailedToValidateError(validationErrors) => BaseError.schemaValidationError(validationErrors = validationErrors)
  }

  def submitDeclaration(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction).async(streamFromMemory) {
      implicit request =>
        logger.info("Version 2 of endpoint has been called")

        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        withTemporaryFile {
          temporaryFile =>
            for {
              validated <- validationService
                .validateXML(MessageType.DepartureDeclaration, request.body.alsoTo(FileIO.toPath(temporaryFile.path)))
                .leftMap(translateValidationError)
            } yield validated
        }.fold[Result](
          baseError => Status(baseError.code.statusCode)(Json.toJson(baseError)),
          _ => Accepted
        )

    }

}
