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
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClientEither
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
import scala.util.control.NonFatal

@ImplementedBy(classOf[ObjectStoreServiceImpl])
trait ObjectStoreService {

  def addMessage(upscanUrl: DownloadUrl, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ObjectStoreError, ObjectSummaryWithMd5]

}

@Singleton
class ObjectStoreServiceImpl @Inject() (clock: Clock, client: PlayObjectStoreClientEither) extends ObjectStoreService with Logging {

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

  override def addMessage(downloadUrl: DownloadUrl, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, ObjectStoreError, ObjectSummaryWithMd5] =
    EitherT {
      val formattedDateTime = dateTimeFormatter.format(OffsetDateTime.ofInstant(clock.instant, ZoneOffset.UTC))

      val urlEither =
        try Right(new URL(downloadUrl.value))
        catch {
          case NonFatal(e) => Left(ObjectStoreError.UnexpectedError(thr = Some(e)))
        }

      urlEither match {
        case Right(upscanURL) =>
          client
            .uploadFromUrl(
              from = upscanURL,
              to = Path.Directory("common-transit-convention-traders").file(s"${movementId.value}-${messageId.value}-$formattedDateTime.xml")
            )
            .map {
              case Right(response) =>
                Right(response)
              case Left(thr) =>
                Left(ObjectStoreError.UnexpectedError(thr = Some(thr)))
            }
        case Left(ex) => Future.successful(Left(ex))
      }
    }

}
