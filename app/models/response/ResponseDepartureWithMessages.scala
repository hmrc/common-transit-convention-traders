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

import controllers.routes
import utils.CallOps._
import models.domain.DepartureWithMessages
import play.api.libs.json.Json
import utils.Utils

object ResponseDepartureWithMessages {

  implicit val format = Json.format[ResponseDepartureWithMessages]

  def apply(d: DepartureWithMessages): ResponseDepartureWithMessages = ResponseDepartureWithMessages(routes.DeparturesController.getDeparture(d.departureId.toString).urlWithContext, d.created, d.updated, d.movementReferenceNumber, d.referenceNumber, d.status, d.messages.map { m => ResponseMessage(m, routes.DepartureMessagesController.getDepartureMessage(d.departureId.toString, Utils.lastFragment(m.location)))})
}

case class ResponseDepartureWithMessages(departure: String, created: LocalDateTime, updated: LocalDateTime, movementReferenceNumber: Option[String], referenceNumber: String, status: String, messages: Seq[ResponseMessage])