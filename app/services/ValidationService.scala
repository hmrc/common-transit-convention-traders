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

package services

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.data.EitherT
import connectors.ValidationConnector
import models.Version
import models.common.errors.ErrorCode
import models.common.errors.FailedToValidateError
import models.common.errors.StandardError
import models.request.MessageType
import models.responses.BusinessValidationResponse
import models.responses.JsonSchemaValidationResponse
import models.responses.XmlSchemaValidationResponse
import play.api.Logging
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class ValidationService @Inject() (validationConnector: ValidationConnector) extends Logging {

  def validateXml(messageType: MessageType, source: Source[ByteString, ?], version: Version)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, FailedToValidateError, Unit] =
    EitherT(
      validationConnector
        .postXml(messageType, source, version)
        .flatMap {
          case None => Future.successful(Right(()))
          case Some(response: BusinessValidationResponse) =>
            Future.successful(Left(FailedToValidateError.BusinessValidationError(response.message)))
          case Some(response: XmlSchemaValidationResponse) =>
            Future.successful(Left(FailedToValidateError.XmlSchemaFailedToValidateError(response.validationErrors)))
        }
        .recover(recoverFromError(messageType))
    )

  def validateJson(messageType: MessageType, source: Source[ByteString, ?], version: Version)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, FailedToValidateError, Unit] =
    EitherT(
      validationConnector
        .postJson(messageType, source, version)
        .flatMap {
          case None => Future.successful(Right(()))
          case Some(response: BusinessValidationResponse) =>
            Future.successful(Left(FailedToValidateError.BusinessValidationError(response.message)))
          case Some(response: JsonSchemaValidationResponse) =>
            Future.successful(Left(FailedToValidateError.JsonSchemaFailedToValidateError(response.validationErrors)))
        }
        .recover(recoverFromError(messageType))
    )

  private def recoverFromError(messageType: MessageType): PartialFunction[Throwable, Either[FailedToValidateError, Unit]] = {
    // A bad request might be returned if the stream doesn't contain XML/JSON, in which case, we need to return a bad request.
    case UpstreamErrorResponse(_, NOT_FOUND, _, _) =>
      // This can only be a message type error
      Left(FailedToValidateError.InvalidMessageTypeError(messageType.toString))
    case UpstreamErrorResponse(message, BAD_REQUEST, _, _) =>
      Json.parse(message).validate[StandardError] match {
        case JsSuccess(StandardError(value, ErrorCode.BadRequest), _) => Left(FailedToValidateError.ParsingError(value))
        case _                                                        => Left(FailedToValidateError.UnexpectedError(None))
      }
    case upstreamError: UpstreamErrorResponse => Left(FailedToValidateError.UnexpectedError(Some(upstreamError)))
    case NonFatal(e) =>
      logger.error(s"Exception occurred while validating request: ${e.getMessage}", e)
      Left(FailedToValidateError.UnexpectedError(Some(e)))
  }

}
