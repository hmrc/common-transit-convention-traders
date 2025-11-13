/*
 * Copyright 2025 HM Revenue & Customs
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

package models

import cats.implicits.*
import models.common.errors.PresentationError
import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue

enum Version(val value: String) {
  case V2_1 extends Version("2.1")
  case V3_0 extends Version("3.0")
}

object Version {

  implicit val format: Format[Version] = new Format[Version] {
    override def writes(o: Version): JsValue = JsString(o.value)

    override def reads(json: JsValue): JsResult[Version] = json match {
      case JsString("3.0") => JsSuccess(V3_0)
      case JsString("2.1") => JsSuccess(V2_1)
      case value           => JsError(s"Invalid version provided: $value")
    }
  }

  def fromString(value: String): Either[PresentationError, Version] =
    Version.values
      .find(_.value == value)
      .toRight(PresentationError.notAcceptableError("Invalid version provided"))
}
