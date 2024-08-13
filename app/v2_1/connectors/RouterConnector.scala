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

package v2_1.connectors

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.codahale.metrics.MetricRegistry
import config.AppConfig
import config.Constants
import metrics.HasMetrics
import metrics.MetricsKeys
import models.common.EORINumber
import models.common.MessageId
import models.common.MovementId
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.http.Status.CREATED
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2_1.models.SubmissionRoute
import v2_1.models.request.MessageType

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[RouterConnectorImpl])
trait RouterConnector {

  def post(messageType: MessageType, eoriNumber: EORINumber, movementId: MovementId, messageId: MessageId, body: Source[ByteString, _])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[SubmissionRoute]

}

class RouterConnectorImpl @Inject() (val metrics: MetricRegistry, httpClientV2: HttpClientV2)(implicit appConfig: AppConfig)
    extends RouterConnector
    with V2BaseConnector
    with HasMetrics
    with Logging {

  override def post(messageType: MessageType, eoriNumber: EORINumber, movementId: MovementId, messageId: MessageId, body: Source[ByteString, _])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[SubmissionRoute] =
    withMetricsTimerAsync(MetricsKeys.RouterBackend.Post) {
      _ =>
        val url = appConfig.routerUrl.withPath(routerRoute(eoriNumber, messageType, movementId, messageId))

        httpClientV2
          .post(url"$url")
          .withInternalAuthToken
          .setHeader(
            HeaderNames.CONTENT_TYPE      -> MimeTypes.XML,
            Constants.XMessageTypeHeader  -> messageType.code,
            Constants.APIVersionHeaderKey -> Constants.APIVersionFinalHeaderValue
          )
          .withBody(body)
          .withClientId
          .execute[HttpResponse]
          .flatMap {
            response =>
              response.status match {
                case CREATED  => Future.successful(SubmissionRoute.ViaEIS)
                case ACCEPTED => Future.successful(SubmissionRoute.ViaSDES)
                case _        => response.error
              }
          }
    }
}
