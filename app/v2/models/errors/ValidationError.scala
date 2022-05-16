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

import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes

object ValidationError {
  implicit val validationErrorReads: Reads[ValidationError]   = Json.reads[ValidationError]
  implicit val validationErrorWrites: Writes[ValidationError] = Json.writes[ValidationError]
}
case class ValidationError(lineNumber: Int, columnNumber: Int, message: String)
