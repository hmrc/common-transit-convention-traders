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

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.google.inject.Inject
import com.codahale.metrics.MetricRegistry
import config.AppConfig
import config.Constants
import io.lemonlabs.uri.Url
import io.lemonlabs.uri.UrlPath
import metrics.HasMetrics
import metrics.MetricsKeys
import models.AuditType
import models.common.EORINumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import models.request.Details
import models.request.MessageType
import models.request.Metadata
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.ws.DefaultBodyWritables
import play.api.libs.ws.JsonBodyWritables
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AuditingConnector @Inject() (httpClient: HttpClientV2, val metrics: MetricRegistry)(implicit appConfig: AppConfig)
    extends BaseConnector
    with DefaultBodyWritables
    with JsonBodyWritables
    with HasMetrics
    with Logging {

  def postMessageType(
    auditType: AuditType,
    contentType: String,
    contentLength: Long,
    payload: Source[ByteString, ?],
    movementId: Option[MovementId] = None,
    messageId: Option[MessageId] = None,
    enrolmentEORI: Option[EORINumber] = None,
    movementType: Option[MovementType] = None,
    messageType: Option[MessageType] = None
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] = withMetricsTimerAsync(MetricsKeys.AuditingBackend.Post) {
    _ =>
      httpClient
        .post(url"${getUrl(auditType)}")
        .withInternalAuthToken
        .withMovementId(movementId)
        .withMessageId(messageId)
        .withEoriNumber(enrolmentEORI)
        .withMovementType(movementType)
        .withMessageType(messageType)
        .withClientId
        .setHeader(
          HeaderNames.CONTENT_TYPE       -> contentType,
          Constants.XContentLengthHeader -> contentLength.toString,
          "X-Audit-Meta-Path"            -> getPath(),
          "X-Audit-Source"               -> "common-transit-convention-traders"
        )
        .withBody(payload)
        .executeAndExpect(ACCEPTED)
  }

  def postStatus(
    auditType: AuditType,
    payload: Option[JsValue],
    movementId: Option[MovementId],
    messageId: Option[MessageId],
    enrolmentEORI: Option[EORINumber],
    movementType: Option[MovementType],
    messageType: Option[MessageType]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] = withMetricsTimerAsync(MetricsKeys.AuditingBackend.Post) {
    _ =>
      val metadata = Metadata(getPath(), movementId, messageId, enrolmentEORI, movementType, messageType)
      val details  = Details(metadata, payload.map(_.as[JsObject]))
      httpClient
        .post(url"${getUrl(auditType)}")
        .withInternalAuthToken
        .setHeader(
          "X-Audit-Source"         -> "common-transit-convention-traders",
          HeaderNames.CONTENT_TYPE -> MimeTypes.JSON
        )
        .withClientId
        .withBody(Json.toJson(details))
        .executeAndExpect(ACCEPTED)
  }

  private def getUrl(auditType: AuditType): Url =
    appConfig.auditingUrl.withPath(UrlPath.parse(s"/transit-movements-auditing/audit/${auditType.name}"))

  private def getPath()(implicit hc: HeaderCarrier): String =
    hc.otherHeaders
      .collectFirst {
        case ("path", value) => value
      }
      .getOrElse("-")
}
