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

package models.formats

import models.errors.BadRequestError
import models.errors.EntityTooLargeError
import models.errors.ErrorCode
import models.errors.ForbiddenError
import models.errors.InternalServiceError
import models.errors.NotFoundError
import models.errors.TransitMovementError
import models.errors.UpstreamServiceError
import play.api.libs.json._

trait HttpFormats extends CommonFormats {

  def withCodeField(jsObject: JsObject, status: String): JsObject =
    jsObject ++ Json.obj(ErrorCode.FieldName -> status)

  implicit val badRequestErrorWrites: OWrites[BadRequestError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit val forbiddenErrorWrites: OWrites[ForbiddenError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit val notFoundErrorWrites: OWrites[NotFoundError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit val entityTooLargeErrorWrites: OWrites[EntityTooLargeError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit val upstreamServiceErrorWrites: OWrites[UpstreamServiceError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit val internalServiceErrorWrites: OWrites[InternalServiceError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit val transitMovementErrorWrites: OWrites[TransitMovementError] = OWrites {
    case err @ BadRequestError(_) =>
      withCodeField(badRequestErrorWrites.writes(err), ErrorCode.BadRequest)
    case err @ NotFoundError(_) =>
      withCodeField(notFoundErrorWrites.writes(err), ErrorCode.NotFound)
    case err @ ForbiddenError(_) =>
      withCodeField(forbiddenErrorWrites.writes(err), ErrorCode.Forbidden)
    case err @ EntityTooLargeError(_) =>
      withCodeField(entityTooLargeErrorWrites.writes(err), ErrorCode.EntityTooLarge)
    case err @ UpstreamServiceError(_, _) =>
      withCodeField(upstreamServiceErrorWrites.writes(err), ErrorCode.InternalServerError)
    case err @ InternalServiceError(_, _) =>
      withCodeField(internalServiceErrorWrites.writes(err), ErrorCode.InternalServerError)
  }
}
