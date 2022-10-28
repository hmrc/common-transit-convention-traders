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
import play.api.http.Status.BAD_REQUEST
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import play.api.libs.json.Reads
import play.api.libs.json.__
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.models.formats.CommonFormats

object PresentationError extends CommonFormats {

  val MessageFieldName = "message"
  val CodeFieldName    = "code"

  def forbiddenError(message: String): PresentationError =
    StandardError(message, ErrorCode.Forbidden)

  def entityTooLargeError(message: String): PresentationError =
    StandardError(message, ErrorCode.EntityTooLarge)

  def unsupportedMediaTypeError(message: String): PresentationError =
    StandardError(message, ErrorCode.UnsupportedMediaType)

  def notAcceptableError(message: String): PresentationError =
    StandardError(message, ErrorCode.NotAcceptable)

  def badRequestError(message: String): PresentationError =
    StandardError(message, ErrorCode.BadRequest)

  def bindingBadRequestError(message: String): PresentationError =
    BindingError(message, BAD_REQUEST, ErrorCode.BadRequest)

  def notFoundError(message: String): PresentationError =
    StandardError(message, ErrorCode.NotFound)

  def unauthorized(message: String): PresentationError =
    StandardError(message, ErrorCode.Unauthorized)

  def xmlSchemaValidationError(
    message: String = "Request failed schema validation",
    validationErrors: NonEmptyList[XmlValidationError]
  ): XmlSchemaValidationError =
    XmlSchemaValidationError(message, ErrorCode.SchemaValidation, validationErrors)

  def jsonSchemaValidationError(
    message: String = "Request failed schema validation",
    validationErrors: NonEmptyList[JsonValidationError]
  ): JsonSchemaValidationError =
    JsonSchemaValidationError(message, ErrorCode.SchemaValidation, validationErrors)

  def upstreamServiceError(
    message: String = "Internal server error",
    code: ErrorCode = ErrorCode.InternalServerError,
    cause: UpstreamErrorResponse
  ): PresentationError =
    UpstreamServiceError(message, code, cause)

  def internalServiceError(
    message: String = "Internal server error",
    code: ErrorCode = ErrorCode.InternalServerError,
    cause: Option[Throwable] = None
  ): PresentationError =
    InternalServiceError(message, code, cause)

  def unapply(error: PresentationError): Option[(String, ErrorCode)] = Some((error.message, error.code))

  private val baseErrorWrites0: OWrites[PresentationError] =
    (
      (__ \ MessageFieldName).write[String] and
        (__ \ CodeFieldName).write[ErrorCode]
    )(unlift(PresentationError.unapply))

  implicit val standardErrorReads: Reads[StandardError] =
    (
      (__ \ MessageFieldName).read[String] and
        (__ \ CodeFieldName).read[ErrorCode]
    )(StandardError.apply _)

  implicit val xmlSchemaErrorFormat: OFormat[XmlSchemaValidationError] =
    (
      (__ \ MessageFieldName).format[String] and
        (__ \ CodeFieldName).format[ErrorCode] and
        (__ \ "validationErrors").format[NonEmptyList[XmlValidationError]]
    )(XmlSchemaValidationError.apply, unlift(XmlSchemaValidationError.unapply))

  implicit val jsonSchemaErrorFormat: OFormat[JsonSchemaValidationError] =
    (
      (__ \ MessageFieldName).format[String] and
        (__ \ CodeFieldName).format[ErrorCode] and
        (__ \ "validationErrors").format[NonEmptyList[JsonValidationError]]
    )(JsonSchemaValidationError.apply, unlift(JsonSchemaValidationError.unapply))

  implicit val baseErrorWrites: OWrites[PresentationError] = OWrites {
    case bindingError: BindingError                           => Json.writes[BindingError].writes(bindingError)
    case xmlSchemaValidationError: XmlSchemaValidationError   => xmlSchemaErrorFormat.writes(xmlSchemaValidationError)
    case jsonSchemaValidationError: JsonSchemaValidationError => jsonSchemaErrorFormat.writes(jsonSchemaValidationError)
    case baseError                                            => baseErrorWrites0.writes(baseError)
  }

}

sealed abstract class PresentationError extends Product with Serializable {
  def message: String
  def code: ErrorCode
}

case class StandardError(message: String, code: ErrorCode) extends PresentationError

case class BindingError(message: String, statusCode: Int, code: ErrorCode) extends PresentationError

case class XmlSchemaValidationError(
  message: String,
  code: ErrorCode,
  validationErrors: NonEmptyList[XmlValidationError]
) extends PresentationError

case class JsonSchemaValidationError(
  message: String,
  code: ErrorCode,
  validationErrors: NonEmptyList[JsonValidationError]
) extends PresentationError

case class UpstreamServiceError(
  message: String = "Internal server error",
  code: ErrorCode = ErrorCode.InternalServerError,
  cause: UpstreamErrorResponse
) extends PresentationError

object UpstreamServiceError {

  def causedBy(cause: UpstreamErrorResponse): PresentationError =
    PresentationError.upstreamServiceError(cause = cause)
}

case class InternalServiceError(
  message: String = "Internal server error",
  code: ErrorCode = ErrorCode.InternalServerError,
  cause: Option[Throwable] = None
) extends PresentationError

object InternalServiceError {

  def causedBy(cause: Throwable): PresentationError =
    PresentationError.internalServiceError(cause = Some(cause))
}
