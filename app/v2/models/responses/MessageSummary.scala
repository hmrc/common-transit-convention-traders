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

import play.api.libs.json.JsPath
import play.api.libs.json.OWrites
import play.api.libs.json.Reads
import v2.models.MessageId
import v2.models.Payload
import v2.models.XmlPayload
import v2.models.request.MessageType
import play.api.libs.functional.syntax._

import java.time.OffsetDateTime

object MessageSummary {

  implicit val messageSummaryReads: Reads[MessageSummary] =
    ((JsPath \ "id").read[MessageId] and
      (JsPath \ "received").read[OffsetDateTime] and
      (JsPath \ "messageType").read[MessageType] and
      (JsPath \ "body").readNullable[XmlPayload])(MessageSummary.apply _)

  implicit val messageSummaryWrites: OWrites[MessageSummary] =
    ((JsPath \ "id").write[MessageId] and
      (JsPath \ "received").write[OffsetDateTime] and
      (JsPath \ "messageType").write[MessageType] and
      (JsPath \ "body").writeNullable[Payload])(unlift(MessageSummary.unapply))

}

case class MessageSummary(
  id: MessageId,
  received: OffsetDateTime,
  messageType: MessageType,
  body: Option[Payload]
)
