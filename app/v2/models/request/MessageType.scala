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

package v2.models.request

import play.api.libs.json.JsError
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.Reads
import play.api.libs.json.Writes

sealed trait MessageType {
  def code: String
  def movementType: String
}

sealed abstract class DepartureMessageType(val code: String) extends MessageType {
  val movementType: String = "departures"
}

object MessageType {
  case object DepartureDeclaration extends DepartureMessageType("IE015")

  val values: Seq[MessageType] = Seq(
    DepartureDeclaration
  )

  def find(code: String): Option[MessageType] =
    values.find(_.code == code)

  implicit val messageTypeReads: Reads[MessageType] = Reads {
    case JsString(value) => find(value).map(JsSuccess(_)).getOrElse(JsError())
    case _               => JsError()
  }

  implicit val messageTypeWrites: Writes[MessageType] = Writes {
    obj => JsString(obj.code)
  }

}
