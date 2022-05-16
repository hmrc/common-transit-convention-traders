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

package v2.models.errors

import cats.data.NonEmptyList
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.__
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.models.formats.CommonFormats

object BaseError extends CommonFormats {

  val MessageFieldName = "message"
  val CodeFieldName    = "code"

  def forbiddenError(message: String): BaseError =
    StandardError(message, ErrorCode.Forbidden)

  def entityTooLargeError(message: String): BaseError =
    StandardError(message, ErrorCode.EntityTooLarge)

  def unsupportedMediaTypeError(message: String): BaseError =
    StandardError(message, ErrorCode.UnsupportedMediaType)

  def badRequestError(message: String): BaseError =
    StandardError(message, ErrorCode.BadRequest)

  def notFoundError(message: String): BaseError =
    StandardError(message, ErrorCode.NotFound)

  def schemaValidationError(message: String = "Request failed schema validation", validationErrors: NonEmptyList[ValidationError]): SchemaValidationError =
    SchemaValidationError(message, ErrorCode.SchemaValidation, validationErrors)

  def upstreamServiceError(
    message: String = "Internal server error",
    code: ErrorCode = ErrorCode.InternalServerError,
    cause: UpstreamErrorResponse
  ): BaseError =
    UpstreamServiceError(message, code, cause)

  def internalServiceError(
    message: String = "Internal server error",
    code: ErrorCode = ErrorCode.InternalServerError,
    cause: Option[Throwable] = None
  ): BaseError =
    InternalServiceError(message, code, cause)

  def unapply(error: BaseError): Option[(String, ErrorCode)] = Some((error.message, error.code))

  private val baseErrorWrites0: OWrites[BaseError] =
    (
      (__ \ MessageFieldName).write[String] and
        (__ \ CodeFieldName).write[ErrorCode]
    )(unlift(BaseError.unapply))

  implicit val standardErrorReads: Reads[StandardError] =
    (
      (__ \ MessageFieldName).read[String] and
        (__ \ CodeFieldName).read[ErrorCode]
    )(StandardError.apply _)

  implicit val schemaErrorFormat: OFormat[SchemaValidationError] =
    (
      (__ \ MessageFieldName).format[String] and
        (__ \ CodeFieldName).format[ErrorCode] and
        (__ \ "validationErrors").format[NonEmptyList[ValidationError]]
    )(SchemaValidationError.apply, unlift(SchemaValidationError.unapply))

  implicit val baseErrorWrites: OWrites[BaseError] = OWrites {
    case schemaValidationError: SchemaValidationError => schemaErrorFormat.writes(schemaValidationError)
    case baseError                                    => baseErrorWrites0.writes(baseError)
  }

}

sealed abstract class BaseError extends Product with Serializable {
  def message: String
  def code: ErrorCode
}

case class StandardError(message: String, code: ErrorCode) extends BaseError
case class SchemaValidationError(
  message: String,
  code: ErrorCode,
  validationErrors: NonEmptyList[ValidationError]
) extends BaseError

case class UpstreamServiceError(
  message: String = "Internal server error",
  code: ErrorCode = ErrorCode.InternalServerError,
  cause: UpstreamErrorResponse
) extends BaseError

object UpstreamServiceError {

  def causedBy(cause: UpstreamErrorResponse): BaseError =
    BaseError.upstreamServiceError(cause = cause)
}

case class InternalServiceError(
  message: String = "Internal server error",
  code: ErrorCode = ErrorCode.InternalServerError,
  cause: Option[Throwable] = None
) extends BaseError

object InternalServiceError {

  def causedBy(cause: Throwable): BaseError =
    BaseError.internalServiceError(cause = Some(cause))
}
