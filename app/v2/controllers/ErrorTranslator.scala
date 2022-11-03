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

import cats.data.EitherT
import v2.models.errors.ConversionError
import v2.models.errors.ExtractionError
import v2.models.errors.FailedToValidateError
import v2.models.errors.StreamingError
import v2.models.errors.PersistenceError
import v2.models.errors.PresentationError
import v2.models.errors.RouterError
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait ErrorTranslator {

  implicit class ErrorConverter[E, A](value: EitherT[Future, E, A]) {

    def asPresentation(implicit c: Converter[E], ec: ExecutionContext): EitherT[Future, PresentationError, A] =
      value.leftMap(c.convert)
  }

  trait Converter[E] {
    def convert(input: E): PresentationError
  }

  val jsonToXmlValidationErrorConverter = new Converter[FailedToValidateError] {
    import v2.models.errors.FailedToValidateError._

    def convert(validationError: FailedToValidateError): PresentationError = validationError match {
      case XmlSchemaFailedToValidateError(_) => PresentationError.internalServiceError(cause = None) // TODO: Determine error message
      case x                                 => validationErrorConverter.convert(x)
    }
  }

  implicit val validationErrorConverter = new Converter[FailedToValidateError] {
    import v2.models.errors.FailedToValidateError._

    def convert(validationError: FailedToValidateError): PresentationError = validationError match {
      case err: UnexpectedError                              => PresentationError.internalServiceError(cause = err.thr)
      case InvalidMessageTypeError(messageType)              => PresentationError.badRequestError(s"$messageType is not a valid message type")
      case XmlSchemaFailedToValidateError(validationErrors)  => PresentationError.xmlSchemaValidationError(validationErrors = validationErrors)
      case JsonSchemaFailedToValidateError(validationErrors) => PresentationError.jsonSchemaValidationError(validationErrors = validationErrors)

    }
  }

  implicit val persistenceErrorConverter = new Converter[PersistenceError] {

    def convert(persistenceError: PersistenceError): PresentationError = persistenceError match {
      case PersistenceError.DepartureNotFound(departureId) =>
        PresentationError.notFoundError(s"Departure movement with ID ${departureId.value} was not found")
      case PersistenceError.MessageNotFound(movement, message) =>
        PresentationError.notFoundError(s"Message with ID ${message.value} for movement ${movement.value} was not found")
      case PersistenceError.DeparturesNotFound(eori) =>
        PresentationError.notFoundError(s"Departure movement IDs for ${eori.value} were not found")
      case err: PersistenceError.UnexpectedError => PresentationError.internalServiceError(cause = err.thr)
    }
  }

  implicit val routerErrorConverter = new Converter[RouterError] {
    import v2.models.errors.RouterError._

    override def convert(routerError: RouterError): PresentationError = routerError match {
      case err: RouterError.UnexpectedError => PresentationError.internalServiceError(cause = err.thr)
      case UnrecognisedOffice =>
        PresentationError.badRequestError(
          "The customs office specified for CustomsOfficeOfDestinationActual or CustomsOfficeOfDeparture must be a customs office located in the United Kingdom"
        )
    }
  }

  implicit val conversionErrorConverter = new Converter[ConversionError] {

    override def convert(routerError: ConversionError): PresentationError = routerError match {
      case err: ConversionError.UnexpectedError => PresentationError.internalServiceError(cause = err.thr)
    }
  }

  implicit val extractionError = new Converter[ExtractionError] {

    override def convert(extractionError: ExtractionError): PresentationError = extractionError match {
      case ExtractionError.MalformedInput                   => PresentationError.badRequestError("Input was malformed")
      case ExtractionError.MessageTypeNotFound(messageType) => PresentationError.badRequestError(s"$messageType is not a valid message type")
    }
  }

  implicit val messageFormatError = new Converter[StreamingError] {
    import v2.models.errors.StreamingError._

    override def convert(messageFormatError: StreamingError): PresentationError = messageFormatError match {
      case UnexpectedError(ex) => PresentationError.internalServiceError(cause = ex)
    }
  }
}
