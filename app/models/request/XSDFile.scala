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

package models.request

sealed trait XSDFile {
  val FilePath: String
  val Label: String
}

sealed trait ArrivalMessage
sealed trait DepartureMessage

object XSDFile {

  object Arrival {
    private val Definitions = Seq(ArrivalNotificationXSD, UnloadingRemarksXSD)

    private val DefinitionsMap = Definitions
      .map(
        xsd => xsd.Label -> xsd
      )
      .toMap

    val SupportedMessages: Map[String, XSDFile] = DefinitionsMap.filter {
      case (_: String, value: XSDFile) =>
        value.isInstanceOf[ArrivalMessage]
    }
  }

  object Departure {
    private val Definitions = Seq(DepartureDeclarationXSD, DeclarationCancellationRequestXSD)

    private val DefinitionsMap = Definitions
      .map(
        xsd => xsd.Label -> xsd
      )
      .toMap

    val SupportedMessages: Map[String, XSDFile] = DefinitionsMap.filter {
      case (_: String, value: XSDFile) =>
        value.isInstanceOf[DepartureMessage]
    }
  }
}

object ArrivalNotificationXSD extends XSDFile {
  val FilePath: String = "/xsd/cc007a.xsd"
  val Label: String    = "CC007A"
}

object DepartureDeclarationXSD extends XSDFile {
  val FilePath: String = "/xsd/cc015b.xsd"
  val Label: String    = "CC015B"
}

object UnloadingRemarksXSD extends XSDFile with ArrivalMessage {
  val FilePath: String = "/xsd/cc044a.xsd"
  val Label: String    = "CC044A"
}

object DeclarationCancellationRequestXSD extends XSDFile with DepartureMessage {
  val FilePath: String = "/xsd/cc014a.xsd"
  val Label: String    = "CC014A"
}
