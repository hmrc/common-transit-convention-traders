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

/** Common error codes documented in [[https://developer.service.hmrc.gov.uk/api-documentation/docs/reference-guide#errors Developer Hub Reference Guide]]
  */
object ErrorCode {
  val FieldName           = "code"
  val BadRequest          = "BAD_REQUEST"
  val NotFound            = "NOT_FOUND"
  val Forbidden           = "FORBIDDEN"
  val InternalServerError = "INTERNAL_SERVER_ERROR"
  val GatewayTimeout      = "GATEWAY_TIMEOUT"
  val SchemaValidation    = "SCHEMA_VALIDATION"
  val EntityTooLarge      = "REQUEST_ENTITY_TOO_LARGE"
}
