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

package services

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
import com.google.inject.Inject
import models.common.errors.ExtractionError
import models.request.MessageType
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class XmlMessageParsingService @Inject() (implicit materializer: Materializer) {

  private val messageSink = Sink.head[Either[ExtractionError, MessageType]]

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

  def extractMessageType(source: Source[ByteString, ?], messageTypeList: Seq[MessageType])(implicit
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
