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
  val FilePath: String
  val Label: String
}

sealed trait ArrivalMessage

object XSDFile {
  object Arrival {
    val Definitions = Seq(ArrivalNotificationXSD, UnloadingRemarksXSD)
    val DefinitionsMap = Definitions.map(xsd => xsd.Label -> xsd).toMap
    val SupportedMessages: Map[String, XSDFile] = DefinitionsMap.filter {
      case (key: String, value: XSDFile) =>
        value.isInstanceOf[ArrivalMessage]
    }
  }
}

object ArrivalNotificationXSD extends XSDFile {
  val FilePath = "/xsd-iconvert/cc007a.xsd"
  val Label = "CC007A"
}

object DepartureDeclarationXSD extends XSDFile {
  val FilePath = "/xsd-iconvert/cc015b.xsd"
  val Label = "CC015B"
}

object UnloadingRemarksXSD extends XSDFile with ArrivalMessage {
  val FilePath = "/xsd-iconvert/cc044a.xsd"
  val Label = "CC044A"
}
