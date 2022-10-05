/*
 * Copyright 2022 HM Revenue & Customs
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

package v2.services

import akka.NotUsed
import akka.stream.alpakka.xml.ParseEvent
import akka.stream.alpakka.xml.StartElement
import akka.stream.scaladsl.Flow
import v2.models.errors.ExtractionError
import v2.models.request.MessageType

object XmlParsers {

  val messageTypeExtractor: Flow[ParseEvent, Either[ExtractionError, MessageType], NotUsed] = Flow[ParseEvent]
    .filter {
      case _: StartElement =>
        true
      case _ => false
    }
    .take(1)
    .map {
      case s: StartElement =>
        MessageType.messageTypeSentByDepartureTrader
          .find(_.rootNode == s.localName)
          .map(Right(_))
          .getOrElse(Left(ExtractionError.MessageTypeNotFound(s.localName)))
      case _ => Left(ExtractionError.MalformedInput())
    }
}
