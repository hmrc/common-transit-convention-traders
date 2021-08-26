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

import java.time.LocalDateTime

import play.api.libs.json.Json

object Arrival {
  implicit val format = Json.format[Arrival]
}

trait BaseArrival {
  def arrivalId: ArrivalId
  def location: String
  def messagesLocation: String
  def movementReferenceNumber: String
  def status: String
  def created: LocalDateTime
  def updated: LocalDateTime
}

case class Arrival(
  arrivalId: ArrivalId,
  location: String,
  messagesLocation: String,
  movementReferenceNumber: String,
  status: String,
  created: LocalDateTime,
  updated: LocalDateTime
) extends BaseArrival {}
