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

package v2_1.services

import org.apache.pekko.stream.Attributes
import org.apache.pekko.stream.Attributes.LogLevels
import org.apache.pekko.stream.FlowShape
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.SinkShape
import org.apache.pekko.stream.connectors.xml.ParseEvent
import org.apache.pekko.stream.connectors.xml.scaladsl.XmlParsing
import org.apache.pekko.stream.scaladsl.GraphDSL
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import models.common.errors.ExtractionError
import uk.gov.hmrc.http.HeaderCarrier
import v2_1.models.request.MessageType

import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[XmlMessageParsingServiceImpl])
trait XmlMessageParsingService {

  def extractMessageType(source: Source[ByteString, _], messageTypeList: Seq[MessageType])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ExtractionError, MessageType]
}

@Singleton
class XmlMessageParsingServiceImpl @Inject() (implicit materializer: Materializer) extends XmlMessageParsingService {

  val messageSink = Sink.head[Either[ExtractionError, MessageType]]

  private def messageTypeSink(messageTypeList: Seq[MessageType]): Sink[ByteString, Future[Either[ExtractionError, MessageType]]] = Sink.fromGraph(
    GraphDSL.createGraph(messageSink) {
      implicit builder => messageShape =>
        import GraphDSL.Implicits._

        val xmlParsing: FlowShape[ByteString, ParseEvent]                            = builder.add(XmlParsing.parser)
        val messageType: FlowShape[ParseEvent, Either[ExtractionError, MessageType]] = builder.add(XmlParsers.messageTypeExtractor(messageTypeList))
        xmlParsing ~> messageType ~> messageShape

        SinkShape(xmlParsing.in)
    }
  )

  override def extractMessageType(source: Source[ByteString, _], messageTypeList: Seq[MessageType])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ExtractionError, MessageType] =
    EitherT(
      source
        .toMat(messageTypeSink(messageTypeList))(Keep.right)
        .withAttributes(
          Attributes.logLevels(
            onFinish = LogLevels.Off // prevents exceptions when traders send malformed XML
          )
        )
        .run()
        .recover {
          case NonFatal(_) => Left(ExtractionError.MalformedInput)
        }
    )

}
