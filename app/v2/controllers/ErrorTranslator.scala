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
import v2.models.errors.FailedToValidateError
import v2.models.errors.PersistenceError
import v2.models.errors.PresentationError
import v2.models.errors.RouterError
import v2.models.errors.FailedToValidateError.InvalidMessageTypeError
import v2.models.errors.FailedToValidateError.UnexpectedError
import v2.models.errors.FailedToValidateError.SchemaFailedToValidateError

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

  implicit val validationErrorConverter = new Converter[FailedToValidateError] {

    def convert(validationError: FailedToValidateError): PresentationError = validationError match {
      case err: UnexpectedError                          => PresentationError.internalServiceError(cause = err.thr)
      case InvalidMessageTypeError(messageType)          => PresentationError.badRequestError(s"$messageType is not a valid message type")
      case SchemaFailedToValidateError(validationErrors) => PresentationError.schemaValidationError(validationErrors = validationErrors)
    }
  }

  implicit val persistenceErrorConverter = new Converter[PersistenceError] {

    def convert(persistenceError: PersistenceError): PresentationError = persistenceError match {
      case err: PersistenceError.UnexpectedError => PresentationError.internalServiceError(cause = err.thr)
    }
  }

  implicit val routerErrorConverter = new Converter[RouterError] {

    override def convert(routerError: RouterError): PresentationError = routerError match {
      case err: RouterError.UnexpectedError => PresentationError.internalServiceError(cause = err.thr)
    }
  }

  implicit val conversionErrorConverter = new Converter[ConversionError] {

    override def convert(routerError: ConversionError): PresentationError = routerError match {
      case err: ConversionError.UnexpectedError => PresentationError.internalServiceError(cause = err.thr)
    }
  }

}
