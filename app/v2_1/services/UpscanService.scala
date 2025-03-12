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

import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import models.common.EORINumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import models.common.errors.UpscanError
import uk.gov.hmrc.http.HeaderCarrier
import v2_1.connectors.UpscanConnector
import v2_1.connectors.V2BaseConnector
import v2_1.models.responses.UpscanInitiateResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import v2_1.models.responses.UpscanResponse.DownloadUrl

@ImplementedBy(classOf[UpscanServiceImpl])
trait UpscanService {

  def upscanInitiate(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): EitherT[Future, UpscanError, UpscanInitiateResponse]

  def upscanGetFile(
    downloadUrl: DownloadUrl
  )(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext,
    materializer: Materializer
  ): EitherT[Future, UpscanError, Source[ByteString, ?]]

}

class UpscanServiceImpl @Inject() (
  upscanConnector: UpscanConnector
) extends UpscanService
    with V2BaseConnector
    with Logging {

  override def upscanInitiate(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): EitherT[Future, UpscanError, UpscanInitiateResponse] =
    EitherT {
      upscanConnector
        .upscanInitiate(eoriNumber, movementType, movementId, messageId)
        .map(Right(_))
        .recover {
          case NonFatal(thr) =>
            logger.error(s"Unable to initiate upscan : ${thr.getMessage}", thr)
            Left(UpscanError.UnexpectedError(thr = Some(thr)))
        }
    }

  override def upscanGetFile(
    downloadUrl: DownloadUrl
  )(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext,
    materializer: Materializer
  ): EitherT[Future, UpscanError, Source[ByteString, ?]] =
    EitherT {
      upscanConnector
        .upscanGetFile(downloadUrl)
        .map(Right(_))
        .recover {
          case NonFatal(thr) =>
            logger.error(s"Unable to get upscan file : ${thr.getMessage}", thr)
            Left(UpscanError.UnexpectedError(thr = Some(thr)))

        }
    }
}
