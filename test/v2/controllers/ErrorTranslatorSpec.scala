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
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import v2.models.errors.BaseError
import v2.models.errors.FailedToValidateError
import v2.models.errors.FailedToValidateError.InvalidMessageTypeError
import v2.models.errors.FailedToValidateError.OtherError
import v2.models.errors.FailedToValidateError.SchemaFailedToValidateError
import v2.models.errors.PersistenceError
import v2.models.errors.ValidationError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ErrorTranslatorSpec
  extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with ScalaCheckDrivenPropertyChecks {

  object Harness extends ErrorTranslator

  import Harness._

  "ErrorConverter#convertError" - {
    "for a success returns the same right" in {
      val input = Right[FailedToValidateError, Unit](()).toEitherT[Future]
      whenReady(input.convertError.value) {
        _ mustBe Right(())
      }
    }

    "for an error returns the same right" in {
      val input = Left[FailedToValidateError, Unit](FailedToValidateError.InvalidMessageTypeError("IE015")).toEitherT[Future]
      whenReady(input.convertError.value) {
        _ mustBe Left(BaseError.badRequestError("IE015 is not a valid message type"))
      }
    }
  }

  "Validation Error" - {

    "an Other Error with no exception returns an internal service error with no exception" in {
      val input = OtherError(None)
      val output = BaseError.internalServiceError()

      validationErrorConverter.convert(input) mustBe output
    }

    "an Other Error with an exception returns an internal service error with an exception" in {
      val exception = new IllegalStateException()
      val input = OtherError(Some(exception))
      val output = BaseError.internalServiceError(cause = Some(exception))

      validationErrorConverter.convert(input) mustBe output
    }

    "an InvalidMessageTypeError returns a bad request error" in forAll(Gen.identifier) {
      messageType =>
        val input = InvalidMessageTypeError(messageType)
        val output = BaseError.badRequestError(s"$messageType is not a valid message type")

        validationErrorConverter.convert(input) mustBe output
    }

    "a SchemaFailedToValidateError returns a schema validation error presentation error" in {
      val validationError = ValidationError(1, 1, "error")
      val input = SchemaFailedToValidateError(NonEmptyList(validationError, Nil))
      val output = BaseError.schemaValidationError(validationErrors = NonEmptyList(validationError, Nil))

      validationErrorConverter.convert(input) mustBe output
    }

  }

  "Persistence Error" - {
    "an Other Error with no exception returns an internal service error with no exception" in {
      val input = PersistenceError.OtherError(None)
      val output = BaseError.internalServiceError()

      persistenceErrorConverter.convert(input) mustBe output
    }

    "an Other Error with an exception returns an internal service error with an exception" in {
      val exception = new IllegalStateException()
      val input = PersistenceError.OtherError(Some(exception))
      val output = BaseError.internalServiceError(cause = Some(exception))

      persistenceErrorConverter.convert(input) mustBe output
    }
  }

}
