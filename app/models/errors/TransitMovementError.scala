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

package models.errors

import uk.gov.hmrc.http.UpstreamErrorResponse

sealed abstract class TransitMovementError extends Product with Serializable

case class ForbiddenError(message: String) extends TransitMovementError

case class BadRequestError(message: String) extends TransitMovementError

case class NotFoundError(message: String) extends TransitMovementError

case class EntityTooLargeError(message: String) extends TransitMovementError

case class UnsupportedMediaTypeError(message: String) extends TransitMovementError

case class UpstreamServiceError(
  message: String = "Internal server error",
  cause: UpstreamErrorResponse
) extends TransitMovementError

object UpstreamServiceError {

  def causedBy(cause: UpstreamErrorResponse): TransitMovementError =
    TransitMovementError.upstreamServiceError(cause = cause)
}

case class InternalServiceError(
  message: String = "Internal server error",
  cause: Option[Throwable] = None
) extends TransitMovementError

object InternalServiceError {

  def causedBy(cause: Throwable): TransitMovementError =
    TransitMovementError.internalServiceError(cause = Some(cause))
}

object TransitMovementError {

  def badRequestError(message: String): TransitMovementError =
    BadRequestError(message)

  def notFoundError(message: String): TransitMovementError =
    NotFoundError(message)

  def upstreamServiceError(
    message: String = "Internal server error",
    cause: UpstreamErrorResponse
  ): TransitMovementError =
    UpstreamServiceError(message, cause)

  def internalServiceError(
    message: String = "Internal server error",
    cause: Option[Throwable] = None
  ): TransitMovementError =
    InternalServiceError(message, cause)
}
