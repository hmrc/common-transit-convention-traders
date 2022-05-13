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

package v2.models

import v2.models.responses.ValidationResponse

sealed trait ValidationError

object ValidationError {
  case class OtherError(thr: Option[Throwable] = None)            extends ValidationError
  case class InvalidMessageTypeError(messageType: String)         extends ValidationError
  case class SchemaValidationError(validationErrors: Seq[String]) extends ValidationError // TODO: fix for correct type
  case object XmlParseError                                       extends ValidationError
}
