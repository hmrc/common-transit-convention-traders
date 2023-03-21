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

import play.api.libs.json.Format
import play.api.libs.json.Json

import scala.util.matching.Regex

object ObjectStoreURI {
  val expectedOwner = "common-transit-convention-traders"

  // The URI consists of the service name in the first part of the path, followed
  // by the location of the object in the context of that service. As this service
  // targets common-transit-convention-traders' objects exclusively, we ensure
  // the URI is targeting that context. This regex ensures that this is the case.
  lazy val expectedUriPattern: Regex = s"^$expectedOwner/(.+)$$".r

  implicit val objectStoreURIformat: Format[ObjectStoreURI] = Json.valueFormat[ObjectStoreURI]
}

case class ObjectStoreURI(value: String) extends AnyVal {

  def asResourceLocation: Option[ObjectStoreResourceLocation] =
    ObjectStoreURI.expectedUriPattern
      .findFirstMatchIn(value)
      .map(_.group(1))
      .map(ObjectStoreResourceLocation.apply)

}
