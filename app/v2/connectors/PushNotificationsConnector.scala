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

package v2.connectors

import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import metrics.HasMetrics
import metrics.MetricsKeys
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.MovementId
import v2.models.request.PushNotificationsAssociation
import v2.models.responses.BoxResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[PushNotificationsConnectorImpl])
trait PushNotificationsConnector {

  def patchAssociation(movementId: MovementId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit]

  def postAssociation(movementId: MovementId, pushNotificationsAssociation: PushNotificationsAssociation)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[BoxResponse]

}

class PushNotificationsConnectorImpl @Inject() (appConfig: AppConfig, httpClientV2: HttpClientV2, val metrics: Metrics)
    extends PushNotificationsConnector
    with V2BaseConnector
    with HasMetrics {

  override def patchAssociation(movementId: MovementId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    withMetricsTimerAsync(MetricsKeys.PushNotificationsBacked.Update) {
      _ =>
        val url = appConfig.pushNotificationsUrl.withPath(pushNotificationsRoute(movementId))

        httpClientV2
          .patch(url"$url")
          .executeAndExpect(NO_CONTENT)
    }

  override def postAssociation(movementId: MovementId, pushNotificationsAssociation: PushNotificationsAssociation)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[BoxResponse] =
    withMetricsTimerAsync(MetricsKeys.PushNotificationsBacked.Post) {
      _ =>
        val url = appConfig.pushNotificationsUrl.withPath(pushNotificationsBoxRoute(movementId))

        httpClientV2
          .post(url"$url")
          .addHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
          .withBody(Json.toJson(pushNotificationsAssociation))
          .execute[BoxResponse]
    }

}
