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

package v2.services

import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import play.api.Logging
import play.api.http.Status.BAD_REQUEST
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.ValidationConnector
import v2.models.errors.FailedToValidateError
import v2.models.request.MessageType

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[ValidationServiceImpl])
trait ValidationService {

  def validateXml(messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, FailedToValidateError, Unit]

  def validateJson(messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, FailedToValidateError, Unit]

}

@Singleton
class ValidationServiceImpl @Inject() (validationConnector: ValidationConnector) extends ValidationService with Logging {

  override def validateXml(messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, FailedToValidateError, Unit] =
    EitherT(
      validationConnector
        .postXml(messageType, source)
        .map {
          case None           => Right(())
          case Some(response) => Left(FailedToValidateError.XmlSchemaFailedToValidateError(response.validationErrors))
        }
        .recover {
          // A bad request might be returned if the stream doesn't contain XML, in which case, we need to return a bad request.
          case UpstreamErrorResponse.Upstream4xxResponse(response) if response.statusCode == BAD_REQUEST =>
            // This can only be a message type error
            Left(FailedToValidateError.InvalidMessageTypeError(messageType.toString))
          case upstreamError: UpstreamErrorResponse => Left(FailedToValidateError.UnexpectedError(Some(upstreamError)))
          case NonFatal(e)                          => Left(FailedToValidateError.UnexpectedError(Some(e)))
        }
    )

  override def validateJson(messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, FailedToValidateError, Unit] =
    EitherT(
      validationConnector
        .postJson(messageType, source)
        .map {
          case None           => Right(())
          case Some(response) => Left(FailedToValidateError.JsonSchemaFailedToValidateError(response.validationErrors))
        }
        .recover {
          // A bad request might be returned if the stream doesn't contain XML/JSON, in which case, we need to return a bad request.
          case UpstreamErrorResponse.Upstream4xxResponse(response) if response.statusCode == BAD_REQUEST =>
            // This can only be a message type error
            Left(FailedToValidateError.InvalidMessageTypeError(messageType.toString))
          case upstreamError: UpstreamErrorResponse => Left(FailedToValidateError.UnexpectedError(Some(upstreamError)))
          case NonFatal(e)                          => Left(FailedToValidateError.UnexpectedError(Some(e)))
        }
    )

}
