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

import com.google.inject.ImplementedBy
import com.google.inject.Inject
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.Implicits._
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.play.FutureEither
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClientEither
import v2.models.MessageId
import v2.models.MovementId
import v2.models.responses.UpscanResponse.DownloadUrl

import java.net.URL
import java.time.Clock
import java.time.ZoneOffset
import javax.inject.Singleton
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[ObjectStoreConnectorImpl])
trait ObjectStoreConnector {

  def postFromUrl(upscanUrl: DownloadUrl, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): FutureEither[ObjectSummaryWithMd5]

}

@Singleton
class ObjectStoreConnectorImpl @Inject() (clock: Clock, client: PlayObjectStoreClientEither) extends ObjectStoreConnector with V2BaseConnector {

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

  override def postFromUrl(upscanUrl: DownloadUrl, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): FutureEither[ObjectSummaryWithMd5] = {

    val formattedDateTime = dateTimeFormatter.format(clock.instant())

    client.uploadFromUrl(
      from = new URL(upscanUrl.value),
      to = Path.Directory("common-transit-convention-traders").file(s"${movementId.value}-${messageId.value}-$formattedDateTime.xml")
    )

  }
}
