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

package v2_1.models.request

import models.common.EORINumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import play.api.libs.json.Json
import play.api.libs.json.OFormat

object Metadata {
  implicit lazy val metadataFormat: OFormat[Metadata] = Json.format[Metadata]
}

case class Metadata(
  path: String,
  movementId: Option[MovementId],
  messageId: Option[MessageId],
  enrolmentEORI: Option[EORINumber],
  movementType: Option[MovementType],
  messageType: Option[MessageType]
)
