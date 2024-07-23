/*
 * Copyright 2023 HM Revenue & Customs
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

package models.response

import play.api.libs.json.OFormat
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.JsString

sealed abstract class JsonErrorResponse {
  def code: String
  def message: String
}

case class JsonSystemErrorResponse(statusCode: Int, message: String) extends JsonErrorResponse {
  val code: String = "SYSTEM"
}

case class XmlParseJsonErrorResponse(message: String) extends JsonErrorResponse {
  val code: String = "PARSE_ERROR"
}

case class JsonClientErrorResponse(statusCode: Int, message: String) {
  val code: String = JsonClientErrorResponse.errorCode
}

object JsonSystemErrorResponse {
  implicit val jsonSystemErrorResponse: OFormat[JsonSystemErrorResponse] = Json.format[JsonSystemErrorResponse]
}

object JsonClientErrorResponse {
  val errorCode: String = "CLIENT_ERROR"

  implicit val jsonClientErrorWrites: OWrites[JsonClientErrorResponse] = OWrites.transform(Json.writes[JsonClientErrorResponse]) {
    case (_, jsObject) => jsObject + ("code" -> JsString(errorCode))
  }
  implicit val jsonClientErrorResponse: OFormat[JsonClientErrorResponse] = OFormat(Json.reads[JsonClientErrorResponse], jsonClientErrorWrites)
}
