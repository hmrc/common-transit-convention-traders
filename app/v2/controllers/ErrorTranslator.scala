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
import v2.models.errors.BaseError
import v2.models.errors.FailedToValidateError
import v2.models.errors.FailedToValidateError.InvalidMessageTypeError
import v2.models.errors.FailedToValidateError.OtherError
import v2.models.errors.FailedToValidateError.SchemaFailedToValidateError
import v2.models.errors.PersistenceError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait ErrorTranslator {

  implicit class ErrorConverter[E, A](value: EitherT[Future, E, A]) {

    def asBaseError(implicit c: Converter[E], ec: ExecutionContext): EitherT[Future, BaseError, A] =
      value.leftMap(c.convert)
  }

  sealed trait Converter[E] {
    def convert(input: E): BaseError
  }

  implicit val validationErrorConverter = new Converter[FailedToValidateError] {

    def convert(validationError: FailedToValidateError): BaseError = validationError match {
      case err: OtherError                               => BaseError.internalServiceError(cause = err.thr)
      case InvalidMessageTypeError(messageType)          => BaseError.badRequestError(s"$messageType is not a valid message type")
      case SchemaFailedToValidateError(validationErrors) => BaseError.schemaValidationError(validationErrors = validationErrors)
    }
  }

  implicit val persistenceErrorConverter = new Converter[PersistenceError] {

    def convert(persistenceError: PersistenceError): BaseError = persistenceError match {
      case err: PersistenceError.OtherError => BaseError.internalServiceError(cause = err.thr)
    }
  }

}
