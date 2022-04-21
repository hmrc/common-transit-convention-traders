/*
 * Copyright 2021 HM Revenue & Customs
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

package models.formats

import cats.data.NonEmptyList
import models.MessageType
import models.errors.BadRequestError
import models.errors.ErrorCode
import models.errors.InternalServiceError
import models.errors.NotFoundError
import models.errors.SchemaValidationError
import models.errors.TransitMovementError
import models.errors.UpstreamServiceError
import models.errors.XmlValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json._

trait HttpFormats extends CommonFormats {

  def withCodeField(jsObject: JsObject, status: String): JsObject =
    jsObject ++ Json.obj(ErrorCode.FieldName -> status)

  implicit val badRequestErrorWrites: OWrites[BadRequestError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit val notFoundErrorWrites: OWrites[NotFoundError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit val schemaValidationError: OFormat[SchemaValidationError] =
    Json.format[SchemaValidationError]

  implicit val xmlValidationErrorWrites: OWrites[XmlValidationError] = (
    (__ \ "message").write[String] and
      (__ \ "errors").write[NonEmptyList[SchemaValidationError]]
  )(
    error => (error.message, error.errors)
  )

  implicit val upstreamServiceErrorWrites: OWrites[UpstreamServiceError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit val internalServiceErrorWrites: OWrites[InternalServiceError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit val transitMovementErrorWrites: OWrites[TransitMovementError] = OWrites {
    case err @ BadRequestError(_) =>
      withCodeField(badRequestErrorWrites.writes(err), ErrorCode.BadRequest)
    case err @ NotFoundError(_) =>
      withCodeField(notFoundErrorWrites.writes(err), ErrorCode.NotFound)
    case err @ ForbiddenError(_, _) =>
      withCodeField(xmlValidationErrorWrites.writes(err), ErrorCode.SchemaValidation)
    case err @ UpstreamServiceError(_, _) =>
      withCodeField(upstreamServiceErrorWrites.writes(err), ErrorCode.InternalServerError)
    case err @ InternalServiceError(_, _) =>
      withCodeField(internalServiceErrorWrites.writes(err), ErrorCode.InternalServerError)
  }

}
