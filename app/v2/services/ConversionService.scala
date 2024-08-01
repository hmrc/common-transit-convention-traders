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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import models.common.errors.ConversionError
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import v2.connectors.ConversionConnector
import v2.models.HeaderType
import v2.models.HeaderTypes
import v2.models.request.MessageType

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[ConversionServiceImpl])
trait ConversionService {

  def convert(messageType: MessageType, source: Source[ByteString, _], headerType: HeaderType)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    materializer: Materializer
  ): EitherT[Future, ConversionError, Source[ByteString, _]]

  def jsonToXml(messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    materializer: Materializer
  ): EitherT[Future, ConversionError, Source[ByteString, _]] = convert(messageType, source, HeaderTypes.jsonToXml)

  def xmlToJson(messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    materializer: Materializer
  ): EitherT[Future, ConversionError, Source[ByteString, _]] = convert(messageType, source, HeaderTypes.xmlToJson)

}

@Singleton
class ConversionServiceImpl @Inject() (conversionConnector: ConversionConnector) extends ConversionService with Logging {

  override def convert(messageType: MessageType, source: Source[ByteString, _], headerType: HeaderType)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    materializer: Materializer
  ): EitherT[Future, ConversionError, Source[ByteString, _]] =
    EitherT(
      conversionConnector
        .post(messageType, source, headerType)
        .map {
          case response => Right(response)
        }
        .recover {
          case NonFatal(e) =>
            logger.error(s"Unable to convert payload : ${e.getMessage}", e)
            Left(ConversionError.UnexpectedError(thr = Some(e)))
        }
    )

}
