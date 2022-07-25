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

import org.xml.sax.SAXParseException
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import play.api.libs.json.Reads
import play.api.libs.json.__

sealed trait ValidationError {
  def message: String
}

object ValidationError {

  implicit lazy val format: OFormat[ValidationError] = OFormat.oFormatFromReadsAndOWrites(reads, writes)

  implicit lazy val reads: Reads[ValidationError] = Json.reads[ValidationError]

  implicit lazy val writes: OWrites[ValidationError] = OWrites {
    case sve: XmlValidationError  => Json.toJsObject(sve)
    case jve: JsonValidationError => Json.toJsObject(jve)
  }
}

case class JsonValidationError(schemaPath: String, message: String) extends ValidationError

object JsonValidationError {

  implicit val jsonValidationErrorReads: Reads[JsonValidationError] =
    (
      (__ \ "schemaPath").read[String] and
        (__ \ "message").read[String]
    )(JsonValidationError.apply _)

  implicit val jsonValidationErrorWrites: OWrites[JsonValidationError] =
    (
      (__ \ "schemaPath").write[String] and
        (__ \ "message").write[String]
    )(unlift(JsonValidationError.unapply))
}

case class XmlValidationError(lineNumber: Int, columnNumber: Int, message: String) extends ValidationError

object XmlValidationError {

  def fromSaxParseException(ex: SAXParseException) =
    XmlValidationError(ex.getLineNumber, ex.getColumnNumber, ex.getMessage)

  implicit val schemaValidationErrorReads: Reads[XmlValidationError] =
    (
      (__ \ "lineNumber").read[Int] and
        (__ \ "columnNumber").read[Int] and
        (__ \ "message").read[String]
    )(XmlValidationError.apply _)

  implicit val schemaValidationErrorWrites: OWrites[XmlValidationError] =
    (
      (__ \ "lineNumber").write[Int] and
        (__ \ "columnNumber").write[Int] and
        (__ \ "message").write[String]
    )(unlift(XmlValidationError.unapply))
}
