/*
 * Copyright 2020 HM Revenue & Customs
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

package models.response

import java.time.LocalDateTime

import models.domain.ArrivalWithMessages
import play.api.libs.json.Json

object ResponseArrivalWithMessages {

  implicit val format = Json.format[ResponseArrivalWithMessages]

  def apply(a: ArrivalWithMessages): ResponseArrivalWithMessages = ResponseArrivalWithMessages(a.location, a.created, a.status, a.messages.map { m => ResponseMessage(m)})
}

case class ResponseArrivalWithMessages(arrival: String, created: LocalDateTime, status: String, messages: Seq[ResponseMessage])