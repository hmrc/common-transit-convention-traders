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
import com.google.inject.ImplementedBy
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.Path
import v2.models.MessageId
import v2.models.MovementId
import v2.models.errors.ObjectStoreError
import v2.models.responses.UpscanResponse.DownloadUrl

import java.net.URL
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

@ImplementedBy(classOf[ObjectStoreServiceImpl])
trait ObjectStoreService {

  def addMessage(upscanUrl: DownloadUrl, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ObjectStoreError, ObjectSummaryWithMd5]

}

@Singleton
class ObjectStoreServiceImpl @Inject() (clock: Clock, client: PlayObjectStoreClient) extends ObjectStoreService with Logging {

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

  override def addMessage(downloadUrl: DownloadUrl, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ObjectStoreError, ObjectSummaryWithMd5] =
    EitherT {
      val formattedDateTime = dateTimeFormatter.format(OffsetDateTime.ofInstant(clock.instant, ZoneOffset.UTC))

      (for {
        url <- Future.fromTry(Try(new URL(downloadUrl.value)))
        response <- client
          .uploadFromUrl(
            from = url,
            to = Path.Directory(s"movements/${movementId.value}").file(s"${movementId.value}-${messageId.value}-$formattedDateTime.xml"),
            owner = "common-transit-convention-traders"
          )
      } yield response)
        .map {
          o => Right(o)
        }
        .recover {
          case NonFatal(thr) => Left(ObjectStoreError.UnexpectedError(Some(thr)))
        }
    }

}
