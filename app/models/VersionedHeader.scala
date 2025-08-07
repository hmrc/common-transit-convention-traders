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

sealed trait VersionedHeader {
  val value: String
}

case class VersionedXmlHeader(version: Version) extends VersionedHeader {
  val value: String = s"application/vnd.hmrc.${version.value}+${XMLHeader.value}"
}
case class VersionedJsonHeader(version: Version) extends VersionedHeader {
  override val value: String = s"application/vnd.hmrc.${version.value}+${JsonHeader.value}"
}
case class VersionedJsonPlusXmlHeader(version: Version) extends VersionedHeader {
  override val value: String = s"application/vnd.hmrc.${version.value}+${JsonPlusXmlHeader.value}"
}
case class VersionedJsonHyphenXmlHeader(version: Version) extends VersionedHeader {
  override val value: String = s"application/vnd.hmrc.${version.value}+${JsonHyphenXmlHeader.value}"
}

object VersionedHeader {
  def fromExtensionAndVersion(contentType: MediaType, version: Version): Either[PresentationError, VersionedHeader] =
    (contentType match {
      case XMLHeader           => VersionedXmlHeader(version)
      case JsonHeader          => VersionedJsonHeader(version)
      case JsonPlusXmlHeader   => VersionedJsonPlusXmlHeader(version)
      case JsonHyphenXmlHeader => VersionedJsonHyphenXmlHeader(version)
    }).asRight
}
