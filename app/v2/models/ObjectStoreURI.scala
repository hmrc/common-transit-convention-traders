package v2.models

import play.api.libs.json.Json

case class ObjectStoreURI(value: String) extends AnyVal

object ObjectStoreURI {
  implicit val format = Json.valueFormat[ObjectStoreURI]
}
