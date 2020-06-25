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

package models.request

sealed trait XSDFile {
  val filePath: String
  val label: String
}

object XSDFile {
  val values = Seq(ArrivalNotificationXSD, DepartureDeclarationXSD, UnloadingRemarksXSD)
  val map = values.map(xsd => xsd.label -> xsd).toMap
  val supportedMessageTypes = map.filterKeys(x => (x != ArrivalNotificationXSD.label) && (x != DepartureDeclarationXSD.label))
}

object ArrivalNotificationXSD extends XSDFile {
  val filePath = "/xsd-iconvert/cc007a.xsd"
  val label = "CC007A"
}

object DepartureDeclarationXSD extends XSDFile {
  val filePath = "/xsd-iconvert/cc015b.xsd"
  val label = "CC015B"
}

object UnloadingRemarksXSD extends XSDFile {
  val filePath = "/xsd-iconvert/cc044a.xsd"
  val label = "CC044A"
}
