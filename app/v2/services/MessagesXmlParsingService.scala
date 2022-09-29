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

import akka.stream.Materializer
import akka.stream.alpakka.xml.StartElement
import akka.stream.alpakka.xml.StartDocument
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import v2.models.errors.ExtractionError
import v2.models.request.MessageType

import javax.inject.Singleton
import scala.concurrent.Future

@ImplementedBy(classOf[MessagesXmlParsingServiceImpl])
trait MessagesXmlParsingService {
  def extractMessageType(source: Source[ByteString, _]): EitherT[Future, ExtractionError, MessageType]
}

@Singleton
class MessagesXmlParsingServiceImpl @Inject() (implicit materializer: Materializer) extends MessagesXmlParsingService {

  override def extractMessageType(source: Source[ByteString, _]): EitherT[Future, ExtractionError, MessageType] =
    EitherT(
      source
        .via(XmlParsing.parser)
        .mapConcat {
          case s: StartElement
              if MessageType.updateDepartureValues
                .exists(_.rootNode == s.localName) =>
            Seq(MessageType.updateDepartureValues.find(_.rootNode == s.localName).get)
          case _ => Seq.empty
        }
        .take(1)
        .fold[Either[ExtractionError, MessageType]](Left(ExtractionError.MessageTypeNotFound("Message Type")))(
          (current, next) =>
            current match {
              case Left(ExtractionError.MessageTypeNotFound(_)) => Right(next)
              case _                                            => Left(ExtractionError.MessageTypeNotFound("Message type"))
            }
        )
        .runWith(Sink.head[Either[ExtractionError, MessageType]])
    )
}
