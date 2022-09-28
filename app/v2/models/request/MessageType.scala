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
  case object IE004 extends DepartureMessageType("IE004")
  case object IE009 extends DepartureMessageType("IE009")
  case object IE013 extends DepartureMessageType("IE013")
  case object IE014 extends DepartureMessageType("IE014")
  case object IE015 extends DepartureMessageType("IE015")
  case object IE019 extends DepartureMessageType("IE019")
  case object IE028 extends DepartureMessageType("IE028")
  case object IE029 extends DepartureMessageType("IE029")
  case object IE035 extends DepartureMessageType("IE035")
  case object IE045 extends DepartureMessageType("IE045")
  case object IE051 extends DepartureMessageType("IE051")
  case object IE054 extends DepartureMessageType("IE054")
  case object IE055 extends DepartureMessageType("IE055")
  case object IE056 extends DepartureMessageType("IE056")
  case object IE060 extends DepartureMessageType("IE060")
  case object IE170 extends DepartureMessageType("IE170")
  case object IE906 extends DepartureMessageType("IE906")
  case object IE928 extends DepartureMessageType("IE928")

  val values: Seq[MessageType] = Seq(
    IE004,
    IE009,
    IE013,
    IE014,
    IE015,
    IE019,
    IE028,
    IE029,
    IE035,
    IE045,
    IE051,
    IE054,
    IE055,
    IE056,
    IE060,
    IE170,
    IE906,
    IE928
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
