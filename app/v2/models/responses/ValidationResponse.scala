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

package v2.models.responses

import cats.data.NonEmptyList
import play.api.libs.functional.syntax.toInvariantFunctorOps
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.OFormat
import play.api.libs.json.__
import v2.models.errors.ValidationError
import v2.models.formats.CommonFormats

object ValidationResponse extends CommonFormats {

  implicit val validationResponseFormat: OFormat[ValidationResponse] =
    (__ \ "validationErrors").format[NonEmptyList[ValidationError]].inmap(ValidationResponse.apply, unlift(ValidationResponse.unapply))

}
case class ValidationResponse(validationErrors: NonEmptyList[ValidationError])
