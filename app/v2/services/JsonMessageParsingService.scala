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
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import uk.gov.hmrc.http.HeaderCarrier
import v2.models.errors.ExtractionError
import v2.models.request.MessageType

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.stream.alpakka.json.scaladsl.JsonReader
import v2.models.errors.ExtractionError.MessageTypeNotFound

@ImplementedBy(classOf[JsonMessageParsingServiceImpl])
trait JsonMessageParsingService {

  def extractMessageType(source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ExtractionError, MessageType]

}

@Singleton
class JsonMessageParsingServiceImpl @Inject() (implicit materializer: Materializer) extends JsonMessageParsingService {

  override def extractMessageType(
    source: Source[ByteString, _]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, ExtractionError, MessageType] =
    EitherT(
      source
        .viaMat(JsonReader.select("$.*.messageType"))(Keep.right)
        .via(Flow.fromFunction(_.utf8String))
        .runWith(Sink.head)
        .map {
          mt =>
            val root = mt.replace("\"", "")
            MessageType.updateMessageTypesSentByDepartureTrader.find(_.rootNode == root) match {
              case Some(messageType) => Right(messageType)
              case None              => Left(MessageTypeNotFound(root))
            }
        }
        .recover {
          case _ => Left(ExtractionError.MalformedInput)
        }
    )
}
