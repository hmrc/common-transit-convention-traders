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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import play.api.Logging
import play.api.http.Status.BAD_REQUEST
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.ConversionConnector
import v2.connectors.ValidationConnector
import v2.models.errors.ConversionError
import v2.models.errors.FailedToValidateError
import v2.models.errors.RouterError
import v2.models.request.MessageType

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[ConversionServiceImpl])
trait ConversionService {

  def convertXmlToJson(messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ConversionError, Source[ByteString, _]]

}

@Singleton
class ConversionServiceImpl @Inject() (conversionConnector: ConversionConnector) extends ConversionService with Logging {

  override def convertXmlToJson(messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ConversionError, Source[ByteString, _]] =
    EitherT(
      conversionConnector
        .post(messageType, source)
        .map {
          case response => Right(response)
        }
        .recover {
          case NonFatal(e) =>
            Left(ConversionError.UnexpectedError(thr = Some(e)))
        }
    )

}
