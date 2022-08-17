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
import v2.base.CommonGenerators
import v2.models.MessageId
import v2.models.DepartureId
import v2.models.errors.FailedToValidateError
import v2.models.errors.JsonValidationError
import v2.models.errors.PersistenceError
import v2.models.errors.PresentationError
import v2.models.errors.RouterError
import v2.models.errors.XmlValidationError
import v2.models.errors.FailedToValidateError.InvalidMessageTypeError
import v2.models.errors.FailedToValidateError.JsonSchemaFailedToValidateError
import v2.models.errors.FailedToValidateError.UnexpectedError
import v2.models.errors.FailedToValidateError.XmlSchemaFailedToValidateError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ErrorTranslatorSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with ScalaCheckDrivenPropertyChecks
    with CommonGenerators {

  object Harness extends ErrorTranslator

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

    "an InvalidMessageTypeError returns a bad request error" in forAll(Gen.identifier) {
      messageType =>
        val input  = InvalidMessageTypeError(messageType)
        val output = PresentationError.badRequestError(s"$messageType is not a valid message type")

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
      val output          = PresentationError.internalServiceError(cause = None)
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
      val departureId = arbitrary[DepartureId].sample.value

      val input  = PersistenceError.DepartureNotFound(departureId)
      val output = PresentationError.notFoundError(s"Departure movement with ID ${departureId.value} was not found")

      persistenceErrorConverter.convert(input) mustBe output
    }

    "a MessageNotFound error returns a NOT_FOUND" in {
      val departureId = arbitrary[DepartureId].sample.value
      val messageId   = arbitrary[MessageId].sample.value

      val input  = PersistenceError.MessageNotFound(departureId, messageId)
      val output = PresentationError.notFoundError(s"Message with ID ${messageId.value} for movement ${departureId.value} was not found")

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
  }

  "Router Error" - {
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
  }

}
