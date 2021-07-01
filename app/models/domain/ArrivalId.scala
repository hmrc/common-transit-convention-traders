/*
 * Copyright 2021 HM Revenue & Customs
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

package models.domain

import play.api.libs.json._
import play.api.mvc.PathBindable

import scala.util.Try

case class ArrivalId(value: Int) extends AnyVal {}

object ArrivalId {

  implicit val arrivalIdFormat = Json.valueFormat[ArrivalId]

//  implicit lazy val pathBindable: PathBindable[ArrivalId] = implicitly[PathBindable[Int]].transform(i => ArrivalId(i), _.value)

}