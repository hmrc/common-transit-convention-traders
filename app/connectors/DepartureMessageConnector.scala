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

import java.time.OffsetDateTime

import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import connectors.util.CustomHttpReader
import javax.inject.Inject
import metrics.{HasMetrics, MetricsKeys}
import models.domain.{DepartureId, DepartureWithMessages, MessageId, MovementMessage}
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class DepartureMessageConnector @Inject() (http: HttpClient, appConfig: AppConfig, val metrics: Metrics) extends BaseConnector with HasMetrics {

  import MetricsKeys.DeparturesBackend._

  def post(message: String, departureId: DepartureId)(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    withMetricsTimerResponse(PostMessage) {
      val url = appConfig.traderAtDeparturesUrl.withPath(departureRoute).addPathParts(departureId.toString, "messages")
      http.POSTString(url.toString, message, requestHeaders(requestHeader))(CustomHttpReader, enforceAuthHeaderCarrier(requestHeaders(requestHeader)), ec)
    }

  def getMessages(
    departureId: DepartureId,
    receivedSince: Option[OffsetDateTime]
  )(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, DepartureWithMessages]] =
    withMetricsTimerAsync(GetMessagesForDeparture) {
      timer =>
        val url = appConfig.traderAtDeparturesUrl.withPath(departureRoute).addPathParts(departureId.toString, "messages")
        val query = receivedSince.map(dt => Seq("receivedSince" -> queryDateFormatter.format(dt))).getOrElse(Seq.empty)
        http.GET[HttpResponse](url.toString, queryParams = query, responseHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(responseHeaders), ec).map {
          response =>
            if (is2xx(response.status)) timer.completeWithSuccess() else timer.completeWithFailure()
            extractIfSuccessful[DepartureWithMessages](response)
        }
    }

  def get(departureId: DepartureId, messageId: MessageId)(implicit
    request: RequestHeader,
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[HttpResponse, MovementMessage]] =
    withMetricsTimerAsync(GetMessageById) {
      timer =>
        val url = appConfig.traderAtDeparturesUrl.withPath(departureRoute).addPathParts(departureId.toString, "messages", messageId.toString)
        http.GET[HttpResponse](url.toString, queryParams = Seq(), responseHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(responseHeaders), ec).map {
          response =>
            if (is2xx(response.status)) timer.completeWithSuccess() else timer.completeWithFailure()
            extractIfSuccessful[MovementMessage](response)
        }
    }

}
