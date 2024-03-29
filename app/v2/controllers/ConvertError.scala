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

  val jsonToXmlValidationErrorConverter = new Converter[FailedToValidateError] {
    import v2.models.errors.FailedToValidateError._

    def convert(validationError: FailedToValidateError): PresentationError = validationError match {
      case XmlSchemaFailedToValidateError(_) => PresentationError.internalServiceError()
      case x                                 => validationErrorConverter.convert(x)
    }
  }

  implicit val validationErrorConverter = new Converter[FailedToValidateError] {
    import v2.models.errors.FailedToValidateError._

    def convert(validationError: FailedToValidateError): PresentationError = validationError match {
      case UnexpectedError(thr)                              => PresentationError.internalServiceError(cause = thr)
      case InvalidMessageTypeError(messageType)              => PresentationError.badRequestError(s"$messageType is not a valid message type")
      case ParsingError(message)                             => PresentationError.badRequestError(message)
      case BusinessValidationError(message)                  => PresentationError.businessValidationError(message)
      case XmlSchemaFailedToValidateError(validationErrors)  => PresentationError.xmlSchemaValidationError(validationErrors = validationErrors)
      case JsonSchemaFailedToValidateError(validationErrors) => PresentationError.jsonSchemaValidationError(validationErrors = validationErrors)
    }
  }

  implicit val persistenceErrorConverter = new Converter[PersistenceError] {
    import v2.models.errors.PersistenceError._

    def convert(persistenceError: PersistenceError): PresentationError = persistenceError match {
      case MovementNotFound(movementId, movementType) =>
        PresentationError.notFoundError(s"${movementType.movementType.capitalize} movement with ID ${movementId.value} was not found")
      case MessageNotFound(movementId, messageId) =>
        PresentationError.notFoundError(s"Message with ID ${messageId.value} for movement ${movementId.value} was not found")
      case PageNotFound         => PresentationError.notFoundError("The requested page does not exist")
      case UnexpectedError(thr) => PresentationError.internalServiceError(cause = thr)
    }
  }

  implicit val routerErrorConverter = new Converter[RouterError] {
    import v2.models.errors.RouterError._

    override def convert(routerError: RouterError): PresentationError = routerError match {
      case UnexpectedError(thr) => PresentationError.internalServiceError(cause = thr)
      case UnrecognisedOffice(office, field) =>
        PresentationError.badRequestError(
          s"The customs office specified for $field must be a customs office located in the United Kingdom ($office was specified)"
        )
      case DuplicateLRN(lrn) => PresentationError.conflictError(s"LRN ${lrn.value} has previously been used and cannot be reused")
    }
  }

  implicit val conversionErrorConverter = new Converter[ConversionError] {
    import v2.models.errors.ConversionError._

    override def convert(routerError: ConversionError): PresentationError = routerError match {
      case UnexpectedError(thr) => PresentationError.internalServiceError(cause = thr)
    }
  }

  implicit val extractionError = new Converter[ExtractionError] {
    import v2.models.errors.ExtractionError._

    override def convert(extractionError: ExtractionError): PresentationError = extractionError match {
      case MalformedInput                   => PresentationError.badRequestError("Input was malformed")
      case MessageTypeNotFound(messageType) => PresentationError.badRequestError(s"$messageType is not a valid message type")
    }
  }

  implicit val messageFormatError = new Converter[StreamingError] {
    import v2.models.errors.StreamingError._

    override def convert(messageFormatError: StreamingError): PresentationError = messageFormatError match {
      case UnexpectedError(ex) => PresentationError.internalServiceError(cause = ex)
    }
  }

  implicit val upscanErrorConverter = new Converter[UpscanError] {
    import v2.models.errors.UpscanError._

    override def convert(upscanInitiateError: UpscanError): PresentationError = upscanInitiateError match {
      case UnexpectedError(thr) => PresentationError.internalServiceError(cause = thr)
    }
  }
}
