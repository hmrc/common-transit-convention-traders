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
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import v2.models.errors.FailedToValidateError
import v2.models.request.MessageType

import javax.inject.Singleton
import scala.concurrent.Future

@ImplementedBy(classOf[MessagesXmlParsingServiceImpl])
trait MessagesXmlParsingService {
  def validateMessageType(source: Source[ByteString, _]): EitherT[Future, FailedToValidateError, MessageType]
}

@Singleton
class MessagesXmlParsingServiceImpl @Inject() (implicit materializer: Materializer) extends MessagesXmlParsingService {

  override def validateMessageType(source: Source[ByteString, _]): EitherT[Future, FailedToValidateError, MessageType] =
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
        .fold[Either[FailedToValidateError, MessageType]](Left(FailedToValidateError.InvalidMessageTypeError("Message Type")))(
          (current, next) =>
            current match {
              case Left(FailedToValidateError.InvalidMessageTypeError(_)) => Right(next)
              case _                                                      => Left(FailedToValidateError.UnexpectedError(None))
            }
        )
        .runWith(Sink.head[Either[FailedToValidateError, MessageType]])
    )

}
