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

package v2_1.connectors

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.codahale.metrics.MetricRegistry
import config.AppConfig
import config.Constants
import io.lemonlabs.uri.QueryString
import metrics.HasMetrics
import metrics.MetricsKeys
import models.common.EORINumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.libs.ws.DefaultBodyWritables
import play.api.libs.ws.JsonBodyWritables
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2_1.models.request.UpscanInitiate
import v2_1.models.responses.UpscanInitiateResponse
import v2_1.models.responses.UpscanResponse.DownloadUrl

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[UpscanConnectorImpl])
trait UpscanConnector {

  def upscanInitiate(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[UpscanInitiateResponse]

  def upscanGetFile(downloadUrl: DownloadUrl)(implicit hc: HeaderCarrier, ec: ExecutionContext, materializer: Materializer): Future[Source[ByteString, ?]]

}

class UpscanConnectorImpl @Inject() (appConfig: AppConfig, httpClientV2: HttpClientV2, val metrics: MetricRegistry)
    extends UpscanConnector
    with V2BaseConnector
    with DefaultBodyWritables
    with JsonBodyWritables
    with HasMetrics {

  override def upscanInitiate(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[UpscanInitiateResponse] =
    withMetricsTimerAsync(MetricsKeys.UpscanInitiateBackend.Post) {
      _ =>
        val queryString =
          if (appConfig.forwardClientIdToUpscan) {
            hc
              .headers(Seq(Constants.XClientIdHeader))
              .headOption
              .map(
                pair => QueryString.fromPairs("clientId" -> pair._2)
              )
              .getOrElse(QueryString.empty)
          } else QueryString.empty

        val callbackUrl =
          appConfig.commmonTransitConventionTradersUrl
            .withPath(attachLargeMessageRoute(eoriNumber, movementType, movementId, messageId))
            .withQueryString(queryString)
            .toString()

        val upscanInitiate =
          UpscanInitiate(callbackUrl, Some(appConfig.upscanMaximumFileSize))

        val url = appConfig.upscanInitiateUrl.withPath(upscanInitiateRoute)

        httpClientV2
          .post(url"$url")
          .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
          .withBody(Json.toJson(upscanInitiate))
          .executeAndDeserialise[UpscanInitiateResponse]
    }

  override def upscanGetFile(
    downloadUrl: DownloadUrl
  )(implicit hc: HeaderCarrier, ec: ExecutionContext, materializer: Materializer): Future[Source[ByteString, ?]] =
    httpClientV2
      .get(url"${downloadUrl.value}")
      .executeAsStream
}
