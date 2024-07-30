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

package v2.connectors

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.codahale.metrics.MetricRegistry
import config.AppConfig
import config.Constants
import io.lemonlabs.uri.Url
import metrics.HasMetrics
import metrics.MetricsKeys
import models.common.EORINumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.request.Details
import v2.models.request.MessageType
import v2.models.request.Metadata
import v2.models.AuditType

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[AuditingConnectorImpl])
trait AuditingConnector {

  def postMessageType(
    auditType: AuditType,
    contentType: String,
    contentLength: Long,
    payload: Source[ByteString, _],
    movementId: Option[MovementId],
    messageId: Option[MessageId],
    enrolmentEORI: Option[EORINumber],
    movementType: Option[MovementType],
    messageType: Option[MessageType]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit]

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
  ): Future[Unit]

}

class AuditingConnectorImpl @Inject() (httpClient: HttpClientV2, val metrics: MetricRegistry)(implicit appConfig: AppConfig)
    extends AuditingConnector
    with V2BaseConnector
    with HasMetrics
    with Logging {

  override def postMessageType(
    auditType: AuditType,
    contentType: String,
    contentLength: Long,
    payload: Source[ByteString, _],
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
      val (url: Url, path: String) = getUrlAndPath(auditType, hc)
      httpClient
        .post(url"$url")
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
          "X-Audit-Meta-Path"            -> path,
          "X-Audit-Source"               -> "common-transit-convention-traders"
        )
        .withBody(payload)
        .executeAndExpect(ACCEPTED)
  }

  override def postStatus(
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
      val (url: Url, path: String) = getUrlAndPath(auditType, hc)
      val metadata                 = Metadata(path, movementId, messageId, enrolmentEORI, movementType, messageType)
      val details                  = Details(metadata, payload.map(_.as[JsObject]))
      httpClient
        .post(url"$url")
        .withInternalAuthToken
        .setHeader(
          "X-Audit-Source"         -> "common-transit-convention-traders",
          HeaderNames.CONTENT_TYPE -> MimeTypes.JSON
        )
        .withClientId
        .withBody(Json.toJson(details))
        .executeAndExpect(ACCEPTED)
  }

  private def getUrlAndPath(auditType: AuditType, hc: HeaderCarrier): (Url, String) = {
    val url = appConfig.auditingUrl.withPath(auditingRoute(auditType))
    val path = hc.otherHeaders
      .collectFirst {
        case ("path", value) => value
      }
      .getOrElse("-")
    (url, path)
  }

}
