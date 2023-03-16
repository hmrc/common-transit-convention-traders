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

package v2.models

import play.api.libs.json._

sealed abstract class MovementType(val movementType: String, val urlFragment: String)

object MovementType {
  final case object Departure extends MovementType("departure", "departures")
  final case object Arrival   extends MovementType("arrival", "arrivals")

  implicit val movementTypeReads: Reads[MovementType] = Reads {
    case x: JsString => MovementType.findByName(x.value).map(JsSuccess(_)).getOrElse(JsError())
    case _           => JsError()
  }

  implicit val movementTypeWrites: Writes[MovementType] = Writes(
    movementType => JsString(movementType.movementType)
  )

  val values = Seq(
    Departure,
    Arrival
  )

  private def findByName(name: String): Option[MovementType] = values.find(_.movementType == name)

}
