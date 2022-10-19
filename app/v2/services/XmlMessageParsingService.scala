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

import akka.stream.FlowShape
import akka.stream.Materializer
import akka.stream.SinkShape
import akka.stream.alpakka.xml.ParseEvent
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import uk.gov.hmrc.http.HeaderCarrier
import v2.models.errors.ExtractionError
import v2.models.request.MessageType

import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[XmlMessageParsingServiceImpl])
trait XmlMessageParsingService { //TODO: Merge the traits and have multiple implementations? Unite and drive via MIMEType?

  def extractMessageType(source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ExtractionError, MessageType]
}

@Singleton
class XmlMessageParsingServiceImpl @Inject() (implicit materializer: Materializer) extends XmlMessageParsingService {

  val messageSink = Sink.head[Either[ExtractionError, MessageType]]

  private val messageTypeSink: Sink[ByteString, Future[Either[ExtractionError, MessageType]]] = Sink.fromGraph(
    GraphDSL.createGraph(messageSink) {
      implicit builder => messageShape =>
        import GraphDSL.Implicits._

        val xmlParsing: FlowShape[ByteString, ParseEvent]                                     = builder.add(XmlParsing.parser)
        val messageTypeDeparture: FlowShape[ParseEvent, Either[ExtractionError, MessageType]] = builder.add(XmlParsers.messageTypeExtractor)
        xmlParsing ~> messageTypeDeparture ~> messageShape

        SinkShape(xmlParsing.in)
    }
  )

  override def extractMessageType(source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ExtractionError, MessageType] =
    EitherT(
      source
        .toMat(messageTypeSink)(Keep.right)
        .run()
        .recover {
          case NonFatal(e) => Left(ExtractionError.MalformedInput())
        }
    )

}
