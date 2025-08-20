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

sealed trait MediaType {
  val value: String
}

case object XMLHeader extends MediaType {
  override val value: String = "xml"
}
case object JsonHeader extends MediaType {
  override val value: String = "json"
}
case object JsonPlusXmlHeader extends MediaType {
  override val value: String = "json+xml"
}
case object JsonHyphenXmlHeader extends MediaType {
  override val value: String = "json-xml"
}

object MediaType {
  private lazy val allExtensions = List(
    XMLHeader,
    JsonHeader,
    JsonPlusXmlHeader,
    JsonHyphenXmlHeader
  )

  def fromString(value: String): Either[PresentationError, MediaType] =
    allExtensions
      .find(_.value == value.toLowerCase())
      .toRight(PresentationError.notAcceptableError("The Accept header is missing or invalid."))
}
