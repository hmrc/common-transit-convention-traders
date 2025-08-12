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

package connectors

import com.codahale.metrics.MetricRegistry
import com.google.inject.Inject
import com.google.inject.Singleton
import config.AppConfig
import io.lemonlabs.uri.UrlPath
import metrics.HasMetrics
import metrics.MetricsKeys
import models.HeaderType
import models.request.MessageType
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.libs.ws.DefaultBodyWritables
import play.api.libs.ws.JsonBodyWritables
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class ConversionConnector @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig, val metrics: MetricRegistry)
    extends BaseConnector
    with HasMetrics
    with DefaultBodyWritables
    with JsonBodyWritables
    with Logging {

  private val conversionBaseRoute: String = "/transit-movements-converter"

  def conversionRoute(messageType: MessageType): UrlPath =
    UrlPath.parse(s"$conversionBaseRoute/messages/${messageType.code}")

  def post(messageType: MessageType, jsonStream: Source[ByteString, ?], headerType: HeaderType)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    materializer: Materializer
  ): Future[Source[ByteString, ?]] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        val url = appConfig.converterUrl.withPath(conversionRoute(messageType))

        httpClientV2
          .post(url"$url")
          .transform(_.addHttpHeaders(headerType.header*))
          .withBody(jsonStream)
          .executeAsStream
    }
}
