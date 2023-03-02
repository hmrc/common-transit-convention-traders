package v2.models.request

import play.api.libs.json.Json
import v2.models.MessageStatus
import v2.models.ObjectStoreURI

case class MessageUpdate(status: MessageStatus, objectStoreURI: Option[ObjectStoreURI])

object MessageUpdate {
  implicit val format = Json.format[MessageUpdate]
}
