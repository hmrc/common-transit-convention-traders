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

package v2.models.responses

import cats.data.NonEmptyList
import play.api.libs.functional.syntax.toInvariantFunctorOps
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.OFormat
import play.api.libs.json.__
import v2.models.errors.JsonValidationError
import v2.models.errors.XmlValidationError
import v2.models.formats.CommonFormats

object XmlValidationResponse extends CommonFormats {

  implicit val validationResponseFormat: OFormat[XmlValidationResponse] =
    (__ \ "validationErrors").format[NonEmptyList[XmlValidationError]].inmap(XmlValidationResponse.apply, unlift(XmlValidationResponse.unapply))

}

case class XmlValidationResponse(validationErrors: NonEmptyList[XmlValidationError])

object JsonValidationResponse extends CommonFormats {

  implicit val validationResponseFormat: OFormat[JsonValidationResponse] =
    (__ \ "validationErrors").format[NonEmptyList[JsonValidationError]].inmap(JsonValidationResponse.apply, unlift(JsonValidationResponse.unapply))

}

case class JsonValidationResponse(validationErrors: NonEmptyList[JsonValidationError])
