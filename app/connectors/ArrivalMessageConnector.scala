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
import models.domain.ArrivalWithMessages
import models.domain.MovementMessage
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import utils.Utils

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ArrivalMessageConnector @Inject() (http: HttpClient, appConfig: AppConfig, val metrics: Metrics) extends BaseConnector with HasMetrics {

  def get(arrivalId: String, messageId: String)(implicit
    requestHeader: RequestHeader,
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[HttpResponse, MovementMessage]] =
    withMetricsTimerAsync("arrival-backend-get-message-by-id") {
      timer =>
        val url = appConfig.traderAtDestinationUrl + s"$arrivalRoute${Utils.urlEncode(arrivalId)}/messages/${Utils.urlEncode(messageId)}"
        http.GET[HttpResponse](url, queryParams = Seq(), responseHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(responseHeaders), ec).map {
          response =>
            if (is2xx(response.status)) timer.completeWithSuccess() else timer.completeWithFailure()
            extractIfSuccessful[MovementMessage](response)
        }
    }

  def post(message: String, arrivalId: String)(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    withMetricsTimerResponse("arrival-backend-post-message") {
      val url = appConfig.traderAtDestinationUrl + s"$arrivalRoute${Utils.urlEncode(arrivalId)}/messages"
      http.POSTString(url, message, requestHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(requestHeaders), ec)
    }

  def getMessages(
    arrivalId: String
  )(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, ArrivalWithMessages]] =
    withMetricsTimerAsync("arrival-backend-get-messages-for-arrival") {
      timer =>
        val url = appConfig.traderAtDestinationUrl + s"$arrivalRoute${Utils.urlEncode(arrivalId)}/messages"
        http.GET[HttpResponse](url, queryParams = Seq(), responseHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(responseHeaders), ec).map {
          response =>
            if (is2xx(response.status)) timer.completeWithSuccess() else timer.completeWithFailure()
            extractIfSuccessful[ArrivalWithMessages](response)
        }
    }

}
