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
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import metrics.HasMetrics
import metrics.MetricsKeys
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.MovementId
import v2.models.request.UpscanInitiate
import v2.models.responses.UpscanInitiateResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[UpscanConnectorImpl])
trait UpscanConnector {

  def upscanInitiate(movementId: MovementId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[UpscanInitiateResponse]

}

class UpscanConnectorImpl @Inject() (appConfig: AppConfig, httpClientV2: HttpClientV2, val metrics: Metrics)
    extends UpscanConnector
    with V2BaseConnector
    with HasMetrics {

  override def upscanInitiate(movementId: MovementId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[UpscanInitiateResponse] =
    withMetricsTimerAsync(MetricsKeys.UpscanInitiateBackend.Post) {
      _ =>
        val callbackUrl = appConfig.commmonTransitConventionTradersUrl.withPath(attachLargeMessageRoute(movementId)).toString()

        val upscanInitiate =
          UpscanInitiate(callbackUrl, Some(appConfig.upscanMaximumFileSize))

        val url = appConfig.upscanInitiateUrl.withPath(upscanInitiateRoute)

        httpClientV2
          .post(url"$url")
          .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
          .withBody(Json.toJson(upscanInitiate))
          .executeAndDeserialise[UpscanInitiateResponse]
    }
}
