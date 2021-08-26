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

import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import connectors.util.CustomHttpReader
import metrics.HasMetrics
import metrics.MetricsKeys
import models.Box
import models.domain.Departure
import models.domain.DepartureId
import models.domain.Departures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.time.OffsetDateTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class DeparturesConnector @Inject() (http: HttpClient, appConfig: AppConfig, val metrics: Metrics) extends BaseConnector with HasMetrics {

  import MetricsKeys.DeparturesBackend._

  def post(
    message: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[UpstreamErrorResponse, ResponseHeaders[Option[Box]]]] =
    withMetricsTimerAsync(Post) {
      _ =>
        val url = appConfig.traderAtDeparturesUrl.withPath(departureRoute)
        http.POSTString[Either[UpstreamErrorResponse, ResponseHeaders[Option[Box]]]](url.toString, message, postPutXmlHeaders)
    }

  def get(departureId: DepartureId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, Departure]] =
    withMetricsTimerAsync(GetById) {
      timer =>
        implicit val customResponseReads: HttpReads[HttpResponse] = CustomHttpReader

        val url = appConfig.traderAtDeparturesUrl.withPath(departureRoute).addPathPart(departureId.toString)

        http.GET[HttpResponse](url.toString, headers = getJsonHeaders).map {
          response =>
            if (is2xx(response.status)) timer.completeWithSuccess() else timer.completeWithFailure()
            extractIfSuccessful[Departure](response)
        }
    }

  def getForEori(
    updatedSince: Option[OffsetDateTime]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, Departures]] =
    withMetricsTimerAsync(GetForEori) {
      timer =>
        implicit val customResponseReads: HttpReads[HttpResponse] = CustomHttpReader

        val url = appConfig.traderAtDeparturesUrl.withPath(departureRoute)

        val query = updatedSince
          .map(
            dt => Seq("updatedSince" -> queryDateFormatter.format(dt))
          )
          .getOrElse(Seq.empty)

        http.GET[HttpResponse](url.toString, queryParams = query, headers = getJsonHeaders).map {
          response =>
            if (is2xx(response.status)) timer.completeWithSuccess() else timer.completeWithFailure()
            extractIfSuccessful[Departures](response)
        }
    }
}
