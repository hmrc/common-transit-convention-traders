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

package models.responses

import cats.data.NonEmptyList
import models.common.errors.JsonValidationError
import models.common.errors.XmlValidationError
import models.formats.CommonFormats
import play.api.libs.functional.syntax.toInvariantFunctorOps
import play.api.libs.json.*

object XmlValidationErrorResponse {

  implicit val reads: Reads[XmlValidationErrorResponse] = Reads {
    case x: JsObject
        if x.fields.exists( // only the business validation error will have "message" as a field
          x => x._1 == "message"
        ) =>
      BusinessValidationResponse.validationResponseFormat.reads(x)
    case x: JsObject =>
      XmlSchemaValidationResponse.validationResponseFormat.reads(x)
    case _ =>
      JsError("Unable to determine error response type")
  }
}

object JsonValidationErrorResponse {

  implicit val reads: Reads[JsonValidationErrorResponse] = Reads {
    case x: JsObject
        if x.fields.exists( // only the business validation error will have "message" as a field
          x => x._1 == "message"
        ) =>
      BusinessValidationResponse.validationResponseFormat.reads(x)
    case x: JsObject =>
      JsonSchemaValidationResponse.validationResponseFormat.reads(x)
    case _ =>
      JsError("Unable to determine error response type")
  }
}

sealed trait XmlValidationErrorResponse

sealed trait JsonValidationErrorResponse

object BusinessValidationResponse extends CommonFormats {

  implicit val validationResponseFormat: OFormat[BusinessValidationResponse] =
    (__ \ "message").format[String].inmap(BusinessValidationResponse.apply, _.message)

}

case class BusinessValidationResponse(message: String) extends XmlValidationErrorResponse with JsonValidationErrorResponse

object XmlSchemaValidationResponse extends CommonFormats {

  implicit val validationResponseFormat: OFormat[XmlSchemaValidationResponse] =
    (__ \ "validationErrors").format[NonEmptyList[XmlValidationError]].inmap(XmlSchemaValidationResponse.apply, _.validationErrors)

}

case class XmlSchemaValidationResponse(validationErrors: NonEmptyList[XmlValidationError]) extends XmlValidationErrorResponse

object JsonSchemaValidationResponse extends CommonFormats {

  implicit val validationResponseFormat: OFormat[JsonSchemaValidationResponse] =
    (__ \ "validationErrors").format[NonEmptyList[JsonValidationError]].inmap(JsonSchemaValidationResponse.apply, _.validationErrors)

}

case class JsonSchemaValidationResponse(validationErrors: NonEmptyList[JsonValidationError]) extends JsonValidationErrorResponse
