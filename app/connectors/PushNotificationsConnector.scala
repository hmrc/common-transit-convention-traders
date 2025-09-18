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
import io.lemonlabs.uri.UrlPath
import metrics.HasMetrics
import metrics.MetricsKeys
import models.Version
import models.common.MessageId
import models.common.MovementId
import models.request.PushNotificationsAssociation
import models.responses.BoxResponse
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class PushNotificationsConnector @Inject() (httpClientV2: HttpClientV2, val metrics: MetricRegistry)(implicit appConfig: AppConfig)
    extends BaseConnector
    with JsonBodyWritables
    with HasMetrics {

  def patchAssociation(movementId: MovementId, version: Version)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    withMetricsTimerAsync(MetricsKeys.PushNotificationsBackend.Update) {
      _ =>
        val url = appConfig.pushNotificationsUrl.withPath(pushNotificationsRoute(movementId))

        httpClientV2
          .patch(url"$url")
          .withInternalAuthToken
          .setHeader("APIVersion" -> s"${version.value}")
          .executeAndExpect(NO_CONTENT)
    }

  def postAssociation(movementId: MovementId, pushNotificationsAssociation: PushNotificationsAssociation, version: Version)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[BoxResponse] =
    withMetricsTimerAsync(MetricsKeys.PushNotificationsBackend.Post) {
      _ =>
        val url = appConfig.pushNotificationsUrl.withPath(pushNotificationsBoxRoute(movementId))

        httpClientV2
          .post(url"$url")
          .withInternalAuthToken
          .setHeader("APIVersion" -> s"${version.value}")
          .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
          .withBody(Json.toJson(pushNotificationsAssociation))
          .execute[BoxResponse]
    }

  def postPpnsSubmissionNotification(movementId: MovementId, messageId: MessageId, body: JsValue, version: Version)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] = {

    val url = appConfig.pushNotificationsUrl.withPath(pushPpnsNotifications(movementId, messageId))

    httpClientV2
      .post(url"$url")
      .withInternalAuthToken
      .setHeader("APIVersion" -> s"${version.value}")
      .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
      .withBody(body)
      .executeAndExpect(ACCEPTED)
  }

  private val pushNotificationsBaseRoute: String = "/transit-movements-push-notifications"

  private def pushNotificationsRoute(movementId: MovementId): UrlPath =
    UrlPath.parse(s"$pushNotificationsBaseRoute/traders/movements/${movementId.value}")

  private def pushNotificationsBoxRoute(movementId: MovementId): UrlPath =
    UrlPath.parse(s"$pushNotificationsBaseRoute/traders/movements/${movementId.value}/box")

  private def pushPpnsNotifications(movementId: MovementId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$pushNotificationsBaseRoute/traders/movements/${movementId.value}/messages/${messageId.value}/submissionNotification")

}
