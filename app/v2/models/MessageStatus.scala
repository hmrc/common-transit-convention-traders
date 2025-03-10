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

import play.api.libs.json.JsError
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes

sealed trait MessageStatus

object MessageStatus {
  case object Received extends MessageStatus

  case object Pending extends MessageStatus

  case object Processing extends MessageStatus

  case object Success extends MessageStatus

  case object Failed extends MessageStatus

  val statusValues: Seq[MessageStatus] = Seq(Received, Pending, Processing, Success, Failed)

  implicit val messageStatusWrites: Writes[MessageStatus] = (status: MessageStatus) => Json.toJson(status.toString)

  implicit val statusReads: Reads[MessageStatus] = Reads {
    case JsString("Received")   => JsSuccess(Received)
    case JsString("Pending")    => JsSuccess(Pending)
    case JsString("Processing") => JsSuccess(Processing)
    case JsString("Success")    => JsSuccess(Success)
    case JsString("Failed")     => JsSuccess(Failed)
    case _                      => JsError("Invalid message status")
  }

}
