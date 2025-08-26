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

import models.MessageStatus
import models.ObjectStoreURI
import models.Payload
import models.XmlPayload
import models.common.MessageId
import models.request.MessageType
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.JsPath
import play.api.libs.json.OWrites
import play.api.libs.json.Reads

import java.time.OffsetDateTime

object MessageSummary {

  implicit val messageSummaryReads: Reads[MessageSummary] =
    ((JsPath \ "id").read[MessageId] and
      (JsPath \ "received").read[OffsetDateTime] and
      (JsPath \ "messageType").readNullable[MessageType] and
      (JsPath \ "body").readNullable[XmlPayload] and
      (JsPath \ "status").readNullable[MessageStatus] and
      (JsPath \ "uri").readNullable[ObjectStoreURI])(MessageSummary.apply)

  implicit val messageSummaryWrites: OWrites[MessageSummary] =
    ((JsPath \ "id").write[MessageId] and
      (JsPath \ "received").write[OffsetDateTime] and
      (JsPath \ "messageType").writeNullable[MessageType] and
      (JsPath \ "body").writeNullable[Payload] and
      (JsPath \ "status").writeNullable[MessageStatus] and
      (JsPath \ "uri").writeNullable[ObjectStoreURI])(
      v => (v.id, v.received, v.messageType, v.body, v.status, v.uri)
    )
}

case class MessageSummary(
  id: MessageId,
  received: OffsetDateTime,
  messageType: Option[MessageType],
  body: Option[Payload],
  status: Option[MessageStatus],
  uri: Option[ObjectStoreURI]
)
