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
import play.api.libs.json.JsResult
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.models.EORINumber
import v2.models.responses.DeclarationResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[PersistenceConnectorImpl])
trait PersistenceConnector {

  def sendDepartureDeclaration(eori: EORINumber, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[DeclarationResponse]

}

@Singleton
class PersistenceConnectorImpl @Inject() (ws: WSClient, appConfig: AppConfig, val metrics: Metrics)
    extends PersistenceConnector
    with HasMetrics
    with V2BaseConnector
    with Logging {

  override def sendDepartureDeclaration(eori: EORINumber, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[DeclarationResponse] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        val url = appConfig.movementsUrl.withPath(movementsPostDeperatureDeclaration(eori))

        // TODO: Temporary, use HttpClientV2 when available
        ws.url(url.toString())
          .addHttpHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          .post(source)
          .flatMap {
            response =>
              response.status match {
                case OK =>
                  response.json
                    .validate[DeclarationResponse]
                    .map(
                      result => Future.successful(result)
                    )
                    .recoverTotal(
                      error => Future.failed(JsResult.Exception(error))
                    )
                case _ =>
                  Future.failed(UpstreamErrorResponse(response.body, response.status))

              }
          }
    }

}
