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

package models.response

import play.api.libs.json.OFormat
import play.api.libs.json.Json
import models.ParseError
import services.XmlError

sealed abstract class JsonErrorResponse {
  def code: String
  def message: String
}

case class MultipleJsonErrorResponse(code: String, message: String, errors: Seq[JsonErrorResponse]) extends JsonErrorResponse {}

case class XmlParseJsonErrorResponse(message: String) extends JsonErrorResponse {
  val code = "PARSE_ERROR"
}

object JsonErrorResponse {
  implicit val jsonErrorResponseFormat: OFormat[JsonErrorResponse] = Json.format[JsonErrorResponse]
}

object MultipleJsonErrorResponse {
  implicit val multipleJsonErrorResponse: OFormat[MultipleJsonErrorResponse] = Json.format[MultipleJsonErrorResponse]
}

object XmlParseJsonErrorResponse {
  implicit val xmlParseJsonErrorResponse: OFormat[XmlParseJsonErrorResponse] = Json.format[XmlParseJsonErrorResponse]

  def fromXmlError(xmlError: XmlError)       = XmlParseJsonErrorResponse(xmlError.reason)
  def fromParseError(parseError: ParseError) = XmlParseJsonErrorResponse(parseError.message)
}
