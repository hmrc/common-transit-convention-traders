/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.Inject

import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import connectors.util.CustomHttpReader
import metrics.HasMetrics
import models.domain.Arrival
import models.domain.Arrivals
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HttpClient, HttpResponse, HeaderCarrier}
import utils.Utils

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ArrivalConnector @Inject() (http: HttpClient, appConfig: AppConfig, val metrics: Metrics) extends BaseConnector with HasMetrics {

  def post(message: String)(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    withMetricsTimerResponse("arrival-backend-post") {
      val url = appConfig.traderAtDestinationUrl + arrivalRoute
      http.POSTString(url, message)(CustomHttpReader, enforceAuthHeaderCarrier(requestHeaders), ec)
    }

  def put(message: String, arrivalId: String)(implicit requestHeader: RequestHeader, headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    withMetricsTimerResponse("arrival-backend-put") {
      val url = appConfig.traderAtDestinationUrl + arrivalRoute + Utils.urlEncode(arrivalId)
      http.PUTString(url, message)(CustomHttpReader, enforceAuthHeaderCarrier(requestHeaders), ec)
    }

  def get(arrivalId: String)(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, Arrival]] =
    withMetricsTimerAsync("arrival-backend-get-by-id") {
      timer =>
        val url = appConfig.traderAtDestinationUrl + arrivalRoute + Utils.urlEncode(arrivalId)
        http.GET[HttpResponse](url, queryParams = Seq(), responseHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(responseHeaders), ec).map {
          response =>
            if (is2xx(response.status)) timer.completeWithSuccess() else timer.completeWithFailure()
            extractIfSuccessful[Arrival](response)
        }
    }

  def getForEori()(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, Arrivals]] =
    withMetricsTimerAsync("arrival-backend-get-for-eori") {
      timer =>
        val url = appConfig.traderAtDestinationUrl + arrivalRoute
        http.GET[HttpResponse](url, queryParams = Seq(), responseHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(responseHeaders), ec).map {
          response =>
            if (is2xx(response.status)) timer.completeWithSuccess() else timer.completeWithFailure()
            extractIfSuccessful[Arrivals](response)
        }
    }
}
