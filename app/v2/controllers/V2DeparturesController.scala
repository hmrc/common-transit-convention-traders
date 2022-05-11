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
import cats.data.EitherT
import cats.syntax.all._
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.Logging
import play.api.libs.Files
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import play.api.mvc.Result
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import v2.connectors.ValidationConnector
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.controllers.actions.MessageSizeAction
import v2.controllers.stream.StreamingParsers
import v2.models.errors.BaseError
import v2.models.errors.InternalServiceError
import v2.models.responses.ValidationResponse

import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[V2DeparturesControllerImpl])
trait V2DeparturesController {

  def submitDeclaration(): Action[Source[ByteString, _]]

}

@Singleton
class V2DeparturesControllerImpl @Inject() (
    val controllerComponents: ControllerComponents,
    authActionNewEnrolmentOnly: AuthNewEnrolmentOnlyAction,
    validationConnector: ValidationConnector,
    messageSizeAction: MessageSizeAction)(implicit val materializer: Materializer) extends BaseController
  with V2DeparturesController
  with Logging
  with StreamingParsers
  with VersionedRouting {

  def submitDeclaration(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction).async(streamFromMemory) {
      implicit request =>
        logger.info("Version 2 of endpoint has been called")

        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        val temporaryFile: Files.TemporaryFile = Files.SingletonTemporaryFileCreator.create()

        // Because we have an open stream, we **must** do something with it. For now, we send it to the ignore sink.
        val fileDirectedSource: Source[ByteString, _] = request.body.alsoTo(FileIO.toPath(temporaryFile.path))

        EitherT(validationConnector
          .validate("cc015c", fileDirectedSource)
          .map {
            httpResponse =>
              Json.fromJson(httpResponse.json)(ValidationResponse.validationResponseFormat) match {
                case JsSuccess(value, _) => Right(value)
                case JsError(errors)     =>
                  // This shouldn't happen - if it does it's something we need to fix.
                  logger.error(s"Failed to parse ValidationResult from Validation Service, the following errors were returned when parsing: ${errors.mkString}")
                  Left(InternalServiceError())
              }
          }
          .recover {
            // A bad request might be returned if the stream doesn't contain XML, in which case, we need to return a bad request.
            case UpstreamErrorResponse.Upstream4xxResponse(response) if response.statusCode == BAD_REQUEST =>
              Left(BaseError.badRequestError(response.message)) // TODO: Check to see what response is returned here, we might need to parse JSON for the message code
            case upstreamError: UpstreamErrorResponse => Left(BaseError.upstreamServiceError(cause = upstreamError))
            case NonFatal(e)                          => Left(BaseError.internalServiceError(cause = Some(e)))
          })
          .fold[Result](
            baseError => Status(baseError.code.statusCode)(Json.toJson(baseError)),
            _         => Accepted
          )
          .flatTap(_ => Future.successful(temporaryFile.delete()))

    }

}
