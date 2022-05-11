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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[ValidationConnectorImpl])
trait ValidationConnector {

  def validate(messageType: String, xmlStream: Source[ByteString, _])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

}

@Singleton
class ValidationConnectorImpl @Inject() (/* httpClient: HttpClientV2, */ appConfig: AppConfig, val metrics: Metrics)
  extends ValidationConnector
    with HasMetrics
    with V2BaseConnector
    with Logging {

  override def validate(messageType: String, xmlStream: Source[ByteString, _])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
          val url = appConfig.validatorUrl.withPath(validationRoute(messageType))
          Future.failed(new IllegalStateException(""))
          //xmlStream.runWith()
          // httpClient.post(url.toJavaURI.toURL).withBody(xmlStream).execute
    }
  }
}