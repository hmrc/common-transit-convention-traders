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

package v2_1.models

import play.api.libs.json.JsError
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes

object Payload {

  implicit val payloadWrites: Writes[Payload] = Writes {
    value => value.asJson
  }

}

object XmlPayload {

  implicit val xmlPayloadReads: Reads[XmlPayload] = Reads {
    case JsString(value) => JsSuccess(XmlPayload(value))
    case _               => JsError()
  }
}

abstract class Payload(val value: String) {
  def asJson: JsValue
}

case class XmlPayload(override val value: String) extends Payload(value) {
  lazy val asJson = JsString(value)
}

case class JsonPayload(override val value: String) extends Payload(value) {
  lazy val asJson = Json.parse(value)
}
