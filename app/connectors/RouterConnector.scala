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
import com.google.inject.Inject
import config.AppConfig
import config.Constants
import io.lemonlabs.uri.UrlPath
import metrics.HasMetrics
import metrics.MetricsKeys
import models.SubmissionRoute
import models.common.EORINumber
import models.common.MessageId
import models.common.MovementId
import models.request.MessageType
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.http.Status.CREATED
import play.api.libs.ws.DefaultBodyWritables
import play.api.libs.ws.JsonBodyWritables
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class RouterConnector @Inject() (val metrics: MetricRegistry, httpClientV2: HttpClientV2)(implicit appConfig: AppConfig)
    extends BaseConnector
    with DefaultBodyWritables
    with JsonBodyWritables
    with HasMetrics
    with Logging {

  private val routerBaseRoute: String = "/transit-movements-router"

  def routerRoute(eoriNumber: EORINumber, messageType: MessageType, movementId: MovementId, messageId: MessageId): UrlPath =
    UrlPath.parse(
      s"$routerBaseRoute/traders/${eoriNumber.value}/movements/${messageType.movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"
    )

  def post(messageType: MessageType, eoriNumber: EORINumber, movementId: MovementId, messageId: MessageId, body: Source[ByteString, ?])(implicit
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
            HeaderNames.CONTENT_TYPE     -> MimeTypes.XML,
            Constants.XMessageTypeHeader -> messageType.code
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
