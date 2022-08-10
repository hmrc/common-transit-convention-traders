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
import play.api.http.MimeTypes
import play.api.http.Status.OK
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.DepartureId
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.responses.DeclarationResponse
import v2.models.responses.MessageIdsResponse
import v2.models.responses.MessageResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[PersistenceConnectorImpl])
trait PersistenceConnector {

  def post(eori: EORINumber, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[DeclarationResponse]

  def getDepartureMessage(eori: EORINumber, departureId: DepartureId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MessageResponse]

  def getDepartureMessageIds(eori: EORINumber, departureId: DepartureId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MessageIdsResponse]
}

@Singleton
class PersistenceConnectorImpl @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig, val metrics: Metrics)
    extends PersistenceConnector
    with HasMetrics
    with V2BaseConnector
    with Logging {

  override def post(eori: EORINumber, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[DeclarationResponse] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        val url = appConfig.movementsUrl.withPath(movementsPostDepartureDeclaration(eori))

        httpClientV2
          .post(url"$url")
          .addHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          .withBody(source)
          .execute[HttpResponse]
          .flatMap {
            response =>
              response.status match {
                case OK => response.as[DeclarationResponse]
                case _  => response.error
              }
          }
    }

  override def getDepartureMessage(eori: EORINumber, departureId: DepartureId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MessageResponse] = {
    val url = appConfig.movementsUrl.withPath(movementsGetDepartureMessage(eori, departureId, messageId))

    httpClientV2
      .get(url"$url")
      .addHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case OK => response.as[MessageResponse]
            case _  => response.error
          }
      }
  }

  override def getDepartureMessageIds(eori: EORINumber, departureId: DepartureId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MessageIdsResponse] = {
    val url = appConfig.movementsUrl.withPath(movementsGetDepartureMessageIds(eori, departureId))

    httpClientV2
      .get(url"$url")
      .addHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case OK => response.as[MessageIdsResponse]
            case _  => response.error
          }
      }
  }

}
