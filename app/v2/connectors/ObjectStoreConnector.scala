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

package v2.connectors

import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.objectstore.client.play.Implicits._
import uk.gov.hmrc.objectstore.client.Md5Hash
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.RetentionPeriod
import uk.gov.hmrc.objectstore.client.play.FutureEither
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClientEither
import v2.models.MessageId
import v2.models.MovementId

import java.net.URL
import java.time.Clock
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[ObjectStoreConnectorImpl])
trait ObjectStoreConnector {

  def postFromUrl(upscanUrl: URL, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): FutureEither[ObjectSummaryWithMd5]

}

class ObjectStoreConnectorImpl @Inject() (client: PlayObjectStoreClientEither, httpClientV2: HttpClientV2, appConfig: AppConfig, clock: Clock)
    extends ObjectStoreConnector
    with V2BaseConnector {

  val dateTimeFormatter = DateTimeFormatter.ofPattern("YYYYMMdd-HHmmss")

  override def postFromUrl(upscanUrl: URL, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): FutureEither[ObjectSummaryWithMd5] = {

    val formattedDateTime = OffsetDateTime.ofInstant(clock.instant, ZoneOffset.UTC).format(dateTimeFormatter)

    client.uploadFromUrl(
      from = upscanUrl,
      to = Path.Directory("common-transit-convention-traders").file(s"$movementId-$messageId-$formattedDateTime.xml")
    )

  }
}
