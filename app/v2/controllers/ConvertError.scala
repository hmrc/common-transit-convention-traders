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

package v2.controllers

import cats.data.EitherT
import play.api.Logging
import v2.models.errors.ExtractionError.MalformedInput
import v2.models.errors.ExtractionError.MessageTypeNotFound
import v2.models.errors.FailedToValidateError.BusinessValidationError
import v2.models.errors.FailedToValidateError.InvalidMessageTypeError
import v2.models.errors.FailedToValidateError.JsonSchemaFailedToValidateError
import v2.models.errors.FailedToValidateError.ParsingError
import v2.models.errors.FailedToValidateError.UnexpectedError
import v2.models.errors.FailedToValidateError.XmlSchemaFailedToValidateError
import v2.models.errors.PersistenceError.MessageNotFound
import v2.models.errors.PersistenceError.MovementNotFound
import v2.models.errors.PersistenceError.PageNotFound
import v2.models.errors.RouterError.DuplicateLRN
import v2.models.errors.RouterError.UnrecognisedOffice
import v2.models.errors._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait ConvertError extends Logging {

  implicit class ErrorConverter[E, A](value: EitherT[Future, E, A]) {

    def asPresentation(implicit c: Converter[E], ec: ExecutionContext): EitherT[Future, PresentationError, A] =
      value.leftMap(c.convert).leftSemiflatTap {
        case InternalServiceError(message, _, cause) =>
          val causeText = cause
            .map {
              ex =>
                s"""
                   |Message: ${ex.getMessage}
                   |Trace: ${ex.getStackTrace.mkString(System.lineSeparator())}
                   |""".stripMargin
            }
            .getOrElse("No exception is available")
          logger.error(s"""Internal Server Error: $message
               |
               |$causeText""".stripMargin)
          Future.successful(())
        case _ => Future.successful(())
      }
  }

  trait Converter[E] {
    def convert(input: E): PresentationError
  }

  val jsonToXmlValidationErrorConverter: Converter[FailedToValidateError] = {
    case XmlSchemaFailedToValidateError(_) => PresentationError.internalServiceError()
    case x                                 => validationErrorConverter.convert(x)
  }

  implicit val validationErrorConverter: Converter[FailedToValidateError] = {
    case UnexpectedError(thr)                              => PresentationError.internalServiceError(cause = thr)
    case InvalidMessageTypeError(messageType)              => PresentationError.badRequestError(s"$messageType is not a valid message type")
    case ParsingError(message)                             => PresentationError.badRequestError(message)
    case BusinessValidationError(message)                  => PresentationError.businessValidationError(message)
    case XmlSchemaFailedToValidateError(validationErrors)  => PresentationError.xmlSchemaValidationError(validationErrors = validationErrors)
    case JsonSchemaFailedToValidateError(validationErrors) => PresentationError.jsonSchemaValidationError(validationErrors = validationErrors)
  }

  implicit val persistenceErrorConverter: Converter[PersistenceError] = {
    case MovementNotFound(movementId, movementType) =>
      PresentationError.notFoundError(s"${movementType.movementType.capitalize} movement with ID ${movementId.value} was not found")
    case MessageNotFound(movementId, messageId) =>
      PresentationError.notFoundError(s"Message with ID ${messageId.value} for movement ${movementId.value} was not found")
    case PageNotFound                          => PresentationError.notFoundError("The requested page does not exist")
    case PersistenceError.UnexpectedError(thr) => PresentationError.internalServiceError(cause = thr)
  }

  implicit val routerErrorConverter: Converter[RouterError] = {
    case RouterError.UnexpectedError(thr) => PresentationError.internalServiceError(cause = thr)
    case UnrecognisedOffice(office, field) =>
      PresentationError.badRequestError(
        s"The customs office specified for $field must be a customs office located in the United Kingdom ($office was specified)"
      )
    case DuplicateLRN(lrn) => PresentationError.conflictError(s"LRN ${lrn.value} has previously been used and cannot be reused")
  }

  implicit val conversionErrorConverter: Converter[ConversionError] = {
    case ConversionError.UnexpectedError(thr) => PresentationError.internalServiceError(cause = thr)
  }

  implicit val extractionError: Converter[ExtractionError] = {
    case MalformedInput                   => PresentationError.badRequestError("Input was malformed")
    case MessageTypeNotFound(messageType) => PresentationError.badRequestError(s"$messageType is not a valid message type")
  }

  implicit val messageFormatError: Converter[StreamingError] = {
    case StreamingError.UnexpectedError(ex) => PresentationError.internalServiceError(cause = ex)
  }

  implicit val upscanErrorConverter: Converter[UpscanError] = {
    case UpscanError.UnexpectedError(thr) => PresentationError.internalServiceError(cause = thr)
  }
}
