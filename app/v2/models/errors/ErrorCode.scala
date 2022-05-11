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

import play.api.libs.json.JsString
import play.api.libs.json.Writes

object ErrorCode {
  val BadRequest: ErrorCode           = ErrorCode("BAD_REQUEST")
  val NotFound: ErrorCode             = ErrorCode("NOT_FOUND")
  val Forbidden: ErrorCode            = ErrorCode("FORBIDDEN")
  val InternalServerError: ErrorCode  = ErrorCode("INTERNAL_SERVER_ERROR")
  val GatewayTimeout: ErrorCode       = ErrorCode("GATEWAY_TIMEOUT")
  val SchemaValidation: ErrorCode     = ErrorCode("SCHEMA_VALIDATION")
  val EntityTooLarge: ErrorCode       = ErrorCode("REQUEST_ENTITY_TOO_LARGE")
  val UnsupportedMediaType: ErrorCode = ErrorCode("UNSUPPORTED_MEDIA_TYPE")

  implicit val errorCodeWrites: Writes[ErrorCode] = Writes {
    errorCode => JsString(errorCode.value)
  }
}

case class ErrorCode private (value: String) extends AnyVal with Product with Serializable
