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
import config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.objectstore.client.Md5Hash
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.RetentionPeriod
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClientEither
import v2.models.MovementId

import java.net.URL
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[ObjectStoreConnectorImpl])
trait ObjectStoreConnector {

  def addMessage(upscanUrl: URL)(implicit hc: HeaderCarrier, ec: ExecutionContext)

}

class ObjectStoreConnectorImpl @Inject() (client: PlayObjectStoreClientEither, httpClientV2: HttpClientV2, appConfig: AppConfig)
    extends ObjectStoreConnector
    with V2BaseConnector {

  override def addMessage(upscanUrl: URL, movementId: MovementId)(implicit hc: HeaderCarrier, ec: ExecutionContext) =
    client.uploadFromUrl(
      from = upscanUrl,
      to = Path.Directory(s"/common-transit-convention-traders/movements/$movementId/").file("summary.txt")
    )
}
