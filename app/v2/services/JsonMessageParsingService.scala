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

package v2.services

import cats.data.EitherT
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.ImplementedBy
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.apache.pekko.util.ByteString
import uk.gov.hmrc.http.HeaderCarrier
import v2.models.errors.ExtractionError
import v2.models.errors.ExtractionError.MessageTypeNotFound
import v2.models.request.MessageType

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Using

@ImplementedBy(classOf[JsonMessageParsingServiceImpl])
trait JsonMessageParsingService {

  def extractMessageType(source: Source[ByteString, _], messageTypeList: Seq[MessageType])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ExtractionError, MessageType]

}

@Singleton
class JsonMessageParsingServiceImpl @Inject() (implicit materializer: Materializer) extends JsonMessageParsingService {

  private val mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

  override def extractMessageType(
    source: Source[ByteString, _],
    messageTypeList: Seq[MessageType]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, ExtractionError, MessageType] =
    EitherT(
      Future.fromTry(Using(source.runWith(StreamConverters.asInputStream(20.seconds))) {
        jsonInput =>
          val jsonNode: JsonNode      = mapper.readTree(jsonInput)
          val rootNode                = jsonNode.fields().next().getKey
          val messageTypeFromRootNode = rootNode.split(":")(1)
          messageTypeList.find(_.rootNode == messageTypeFromRootNode) match {
            case Some(messageType) => Right(messageType)
            case None              => Left(MessageTypeNotFound(messageTypeFromRootNode))
          }
      }.recover {
        case _ => Left(ExtractionError.MalformedInput)
      })
    )

}
