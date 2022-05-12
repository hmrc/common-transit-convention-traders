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
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.ValidationConnector
import v2.models.errors.BaseError
import v2.models.errors.InternalServiceError
import v2.models.request.MessageType
import v2.models.responses.ValidationResponse

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[ValidationServiceImpl])
trait ValidationService {

  def validateXML(messageType: MessageType, source: Source[ByteString, _])(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, BaseError, Unit]

}

@Singleton
class ValidationServiceImpl @Inject() (validationConnector: ValidationConnector) extends ValidationService with Logging {

  override def validateXML(messageType: MessageType, source: Source[ByteString, _])(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, BaseError, Unit] =
    EitherT(validationConnector
      .validate(messageType, source)
      .map {
        jsonValue =>
          Json.fromJson(jsonValue)(ValidationResponse.validationResponseFormat) match {
            case JsSuccess(value, _) =>
              if (value.validationErrors.isEmpty) Right(())
              else Left(BaseError.schemaValidationError(validationErrors = value.validationErrors))
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


}
