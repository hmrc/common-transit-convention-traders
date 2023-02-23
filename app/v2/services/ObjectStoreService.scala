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

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import v2.connectors.ConversionConnector
import v2.connectors.ObjectStoreConnector
import v2.models.HeaderType
import v2.models.HeaderTypes
import v2.models.MessageId
import v2.models.MovementId
import v2.models.errors.ConversionError
import v2.models.errors.ObjectStoreError
import v2.models.request.MessageType

import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[ObjectStoreServiceImpl])
trait ObjectStoreService {

  def addMessage(upscanUrl: URL, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ConversionError, Source[ByteString, _]]

}

@Singleton
class ObjectStoreServiceImpl @Inject() (objectStoreConnector: ObjectStoreConnector) extends ObjectStoreService with Logging {

  override def addMessage(upscanUrl: URL, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ConversionError, Source[ByteString, _]] =
    EitherT(
      objectStoreConnector
        .postFromUrl(upscanUrl, movementId, messageId)
        .map {
          case Right(response) => Right(response)
          case Left(thr)       => Left(ObjectStoreError.UnexpectedError(thr = Some(thr)))
        }
        .recover {
          case NonFatal(e) =>
            Left(ConversionError.UnexpectedError(thr = Some(e)))
        }
    )

}