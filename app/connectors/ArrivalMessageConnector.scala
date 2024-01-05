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
import config.AppConfig
import connectors.util.CustomHttpReader
import metrics.HasMetrics
import metrics.MetricsKeys
import models.domain.ArrivalId
import models.domain.ArrivalWithMessages
import models.domain.MessageId
import models.domain.MovementMessage
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpResponse

import java.time.OffsetDateTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ArrivalMessageConnector @Inject() (http: HttpClient, appConfig: AppConfig, val metrics: MetricRegistry) extends BaseConnector with HasMetrics {

  import MetricsKeys.ArrivalBackend._

  def get(arrivalId: ArrivalId, messageId: MessageId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, MovementMessage]] =
    withMetricsTimerAsync(GetMessageById) {
      timer =>
        implicit val customResponseReads: HttpReads[HttpResponse] = CustomHttpReader

        val url = appConfig.traderAtDestinationUrl.withPath(arrivalRoute).addPathParts(arrivalId.toString, "messages", messageId.toString)

        http.GET[HttpResponse](url.toString, headers = getJsonHeaders).map {
          response =>
            if (is2xx(response.status)) timer.completeWithSuccess() else timer.completeWithFailure()
            extractIfSuccessful[MovementMessage](response)
        }
    }

  def post(message: String, arrivalId: ArrivalId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    withMetricsTimerResponse(PostMessage) {
      implicit val customResponseReads: HttpReads[HttpResponse] = CustomHttpReader

      val url = appConfig.traderAtDestinationUrl.withPath(arrivalRoute).addPathParts(arrivalId.toString, "messages")

      http.POSTString[HttpResponse](url.toString, message, postPutXmlHeaders)
    }

  def getMessages(arrivalId: ArrivalId, receivedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[HttpResponse, ArrivalWithMessages]] =
    withMetricsTimerAsync(GetMessagesForArrival) {
      timer =>
        implicit val customResponseReads: HttpReads[HttpResponse] = CustomHttpReader

        val url = appConfig.traderAtDestinationUrl.withPath(arrivalRoute).addPathParts(arrivalId.toString, "messages")

        val query = receivedSince
          .map(
            dt => Seq("receivedSince" -> queryDateFormatter.format(dt))
          )
          .getOrElse(Seq.empty)

        http.GET[HttpResponse](url.toString, queryParams = query, headers = getJsonHeaders).map {
          response =>
            if (is2xx(response.status)) timer.completeWithSuccess() else timer.completeWithFailure()
            extractIfSuccessful[ArrivalWithMessages](response)
        }
    }

}
