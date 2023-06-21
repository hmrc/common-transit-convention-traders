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

import cats.data.NonEmptyList
import cats.syntax.all._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Logging
import v2.base.TestCommonGenerators
import v2.models.EORINumber
import v2.models.LocalReferenceNumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.errors.ConversionError
import v2.models.errors.ExtractionError
import v2.models.errors.FailedToValidateError
import v2.models.errors.JsonValidationError
import v2.models.errors.PersistenceError
import v2.models.errors.PresentationError
import v2.models.errors.RouterError
import v2.models.errors.StreamingError
import v2.models.errors.UpscanError
import v2.models.errors.XmlValidationError
import v2.models.errors.FailedToValidateError.BusinessValidationError
import v2.models.errors.FailedToValidateError.InvalidMessageTypeError
import v2.models.errors.FailedToValidateError.JsonSchemaFailedToValidateError
import v2.models.errors.FailedToValidateError.ParsingError
import v2.models.errors.FailedToValidateError.UnexpectedError
import v2.models.errors.FailedToValidateError.XmlSchemaFailedToValidateError
import v2.models.errors.PersistenceError.DuplicateLRNError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConvertErrorSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with ScalaCheckDrivenPropertyChecks
    with TestCommonGenerators {

  object Harness extends ConvertError with Logging

  import Harness._

  "ErrorConverter#asPresentation" - {
    "for a success returns the same right" in {
      val input = Right[FailedToValidateError, Unit](()).toEitherT[Future]
      whenReady(input.asPresentation.value) {
        _ mustBe Right(())
      }
    }

    "for an error returns a left with the appropriate presentation error" in {
      val input = Left[FailedToValidateError, Unit](FailedToValidateError.InvalidMessageTypeError("IE015")).toEitherT[Future]
      whenReady(input.asPresentation.value) {
        _ mustBe Left(PresentationError.badRequestError("IE015 is not a valid message type"))
      }
    }
  }

  "Validation Error" - {

    "an Unexpected Error with no exception returns an internal service error with no exception" in {
      val input  = UnexpectedError(None)
      val output = PresentationError.internalServiceError()

      validationErrorConverter.convert(input) mustBe output
    }

    "an Unexpected Error with an exception returns an internal service error with an exception" in {
      val exception = new IllegalStateException()
      val input     = UnexpectedError(Some(exception))
      val output    = PresentationError.internalServiceError(cause = Some(exception))

      validationErrorConverter.convert(input) mustBe output
    }

    "a ParseError returns a bad request error" in forAll(Gen.identifier) {
      message =>
        val input  = ParsingError(message)
        val output = PresentationError.badRequestError(message)

        validationErrorConverter.convert(input) mustBe output
    }

    "an InvalidMessageTypeError returns a bad request error" in forAll(Gen.identifier) {
      messageType =>
        val input  = InvalidMessageTypeError(messageType)
        val output = PresentationError.badRequestError(s"$messageType is not a valid message type")

        validationErrorConverter.convert(input) mustBe output
    }

    "a BusinessValidationError returns a bad request error" in forAll(Gen.identifier) {
      message =>
        val input  = BusinessValidationError(message)
        val output = PresentationError.businessValidationError(message)

        validationErrorConverter.convert(input) mustBe output
    }

    "a XmlSchemaFailedToValidateError returns a schema validation error presentation error" in {
      val validationError = XmlValidationError(1, 1, "error")
      val input           = XmlSchemaFailedToValidateError(NonEmptyList(validationError, Nil))
      val output          = PresentationError.xmlSchemaValidationError(validationErrors = NonEmptyList(validationError, Nil))

      validationErrorConverter.convert(input) mustBe output
    }

    "jsonToXmlValidationErrorConverter special case validation error" in {
      val validationError = XmlValidationError(1, 1, "empty")
      val input           = XmlSchemaFailedToValidateError(NonEmptyList(validationError, Nil))
      val output          = PresentationError.internalServiceError()
      jsonToXmlValidationErrorConverter.convert(input) mustBe output
    }

    "jsonToXmlValidationErrorConverter should forward other cases to standard conversion" in {
      val validationError = JsonValidationError("path", "message")
      val input           = JsonSchemaFailedToValidateError(NonEmptyList(validationError, Nil))
      val output          = PresentationError.jsonSchemaValidationError(validationErrors = NonEmptyList(validationError, Nil))
      jsonToXmlValidationErrorConverter.convert(input) mustBe output
    }

    "a JsonSchemaFailedToValidateError returns a schema validation error presentation error" in {
      val validationError = JsonValidationError("path", "error")
      val input           = JsonSchemaFailedToValidateError(NonEmptyList(validationError, Nil))
      val output          = PresentationError.jsonSchemaValidationError(validationErrors = NonEmptyList(validationError, Nil))

      validationErrorConverter.convert(input) mustBe output
    }

  }

  "Persistence Error" - {
    "a DepartureNotFound error returns a NOT_FOUND" in {
      val departureId = arbitrary[MovementId].sample.value

      val input  = PersistenceError.MovementNotFound(departureId, MovementType.Departure)
      val output = PresentationError.notFoundError(s"Departure movement with ID ${departureId.value} was not found")

      persistenceErrorConverter.convert(input) mustBe output
    }

    "an ArrivalNotFound error returns a NOT_FOUND" in {
      val arrivalId = arbitrary[MovementId].sample.value

      val input  = PersistenceError.MovementNotFound(arrivalId, MovementType.Arrival)
      val output = PresentationError.notFoundError(s"Arrival movement with ID ${arrivalId.value} was not found")

      persistenceErrorConverter.convert(input) mustBe output
    }

    "a MessageNotFound error returns a NOT_FOUND" in {
      val departureId = arbitrary[MovementId].sample.value
      val messageId   = arbitrary[MessageId].sample.value

      val input  = PersistenceError.MessageNotFound(departureId, messageId)
      val output = PresentationError.notFoundError(s"Message with ID ${messageId.value} for movement ${departureId.value} was not found")

      persistenceErrorConverter.convert(input) mustBe output
    }

    "ArrivalNotFound error returns a NOT_FOUND" in {
      val movementId = arbitrary[MovementId].sample.value

      val input  = PersistenceError.MovementNotFound(movementId, MovementType.Arrival)
      val output = PresentationError.notFoundError(s"Arrival movement with ID ${movementId.value} was not found")

      persistenceErrorConverter.convert(input) mustBe output
    }

    "an Unexpected Error with no exception returns an internal service error with no exception" in {
      val input  = PersistenceError.UnexpectedError(None)
      val output = PresentationError.internalServiceError()

      persistenceErrorConverter.convert(input) mustBe output
    }

    "an Unexpected Error with an exception returns an internal service error with an exception" in {
      val exception = new IllegalStateException()
      val input     = PersistenceError.UnexpectedError(Some(exception))
      val output    = PresentationError.internalServiceError(cause = Some(exception))

      persistenceErrorConverter.convert(input) mustBe output
    }

    "Departure - LRN + MessageSender already exists returns a conflict error with no exception" in {
      val lrn    = LocalReferenceNumber("1234")
      val input  = DuplicateLRNError(lrn)
      val output = PresentationError.conflictError(s"LRN ${lrn.value} has previously been used and cannot be reused")

      persistenceErrorConverter.convert(input) mustBe output
    }

  }

  "Router Error" - {
    "an UnrecognisedOffice error returns a bad request error" in forAll(Gen.alphaNumStr, Gen.alphaStr) {
      (office, field) =>
        val input = RouterError.UnrecognisedOffice(office, field)
        val output = PresentationError.badRequestError(
          s"The customs office specified for $field must be a customs office located in the United Kingdom ($office was specified)"
        )

        routerErrorConverter.convert(input) mustBe output
    }

    "an Unexpected Error with no exception returns an internal service error with no exception" in {
      val input  = RouterError.UnexpectedError(None)
      val output = PresentationError.internalServiceError()

      routerErrorConverter.convert(input) mustBe output
    }

    "an Unexpected Error with an exception returns an internal service error with an exception" in {
      val exception = new IllegalStateException()
      val input     = RouterError.UnexpectedError(Some(exception))
      val output    = PresentationError.internalServiceError(cause = Some(exception))

      routerErrorConverter.convert(input) mustBe output
    }

    "a Duplicate LRN Error returns conflict error" in {
      val lrn    = LocalReferenceNumber("1234")
      val input  = RouterError.DuplicateLRN(lrn)
      val output = PresentationError.conflictError(s"LRN ${lrn.value} has previously been used and cannot be reused")

      routerErrorConverter.convert(input) mustBe output
    }
  }

  "Conversion Error" - {
    "an Unexpected Error with no exception returns an internal service error with no exception" in {
      val input  = ConversionError.UnexpectedError(None)
      val output = PresentationError.internalServiceError()

      conversionErrorConverter.convert(input) mustBe output
    }

    "an Unexpected Error with an exception returns an internal service error with an exception" in {
      val exception = new IllegalStateException()
      val input     = ConversionError.UnexpectedError(Some(exception))
      val output    = PresentationError.internalServiceError(cause = Some(exception))

      conversionErrorConverter.convert(input) mustBe output
    }
  }

  "Extraction Error" - {
    "a MalformedInput error returns a bad request error" in {
      val input  = ExtractionError.MalformedInput
      val output = PresentationError.badRequestError("Input was malformed")

      extractionError.convert(input) mustBe output
    }

    "a MessageTypeNotFound error returns a bad request error" in {
      val input  = ExtractionError.MessageTypeNotFound("IE123456")
      val output = PresentationError.badRequestError("IE123456 is not a valid message type")

      extractionError.convert(input) mustBe output
    }
  }

  "Message Format Error" - {
    "an Unexpected Error with no exception returns an internal service error with no exception" in {
      val input  = StreamingError.UnexpectedError(None)
      val output = PresentationError.internalServiceError()

      messageFormatError.convert(input) mustBe output
    }

    "an Unexpected Error with an exception returns an internal service error with an exception" in {
      val exception = new IllegalStateException()
      val input     = StreamingError.UnexpectedError(Some(exception))
      val output    = PresentationError.internalServiceError(cause = Some(exception))

      messageFormatError.convert(input) mustBe output
    }
  }

  "UpscanInitiate Error" - {
    "an Unexpected Error with no exception returns an internal service error with no exception" in {
      val input  = UpscanError.UnexpectedError(None)
      val output = PresentationError.internalServiceError()

      upscanErrorConverter.convert(input) mustBe output
    }

    "an Unexpected Error with an exception returns an internal service error with an exception" in {
      val exception = new IllegalStateException()
      val input     = UpscanError.UnexpectedError(Some(exception))
      val output    = PresentationError.internalServiceError(cause = Some(exception))

      upscanErrorConverter.convert(input) mustBe output
    }
  }
}
