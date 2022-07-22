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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import metrics.HasMetrics
import metrics.MetricsKeys
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.Status.OK
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.models.request.MessageType
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[ValidationConnectorImpl])
trait ConversionConnector {

  def post(messageType: MessageType, xmlStream: Source[ByteString, _], contentType: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Source[ByteString, _]]

}

@Singleton
class ConversionConnectorImpl @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig, val metrics: Metrics)
    extends ConversionConnector
    with HasMetrics
    with V2BaseConnector
    with Logging {

  override def post(messageType: MessageType, xmlStream: Source[ByteString, _], contentType: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Source[ByteString, _]] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        val url = appConfig.converterUrl.withPath(conversionRoute(messageType))

        httpClientV2
          .post(url"$url")
          .addHeaders(HeaderNames.CONTENT_TYPE -> contentType)
          .withBody(xmlStream)
          .execute[HttpResponse]
          .flatMap {
            response =>
              response.status match {
                case OK =>
                  Future.successful(response.bodyAsSource)
                case _ =>
                  Future.failed(UpstreamErrorResponse(response.body, response.status))
              }
          }
    }
}
