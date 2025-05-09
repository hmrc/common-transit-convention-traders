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
import com.google.inject.Singleton
import com.codahale.metrics.MetricRegistry
import config.AppConfig
import metrics.HasMetrics
import metrics.MetricsKeys
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.NO_CONTENT
import play.api.http.Status.OK
import play.api.libs.json.Reads
import play.api.libs.ws.DefaultBodyWritables
import play.api.libs.ws.JsonBodyWritables
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2_1.models.request.MessageType
import v2_1.models.responses.JsonValidationErrorResponse
import v2_1.models.responses.XmlValidationErrorResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[ValidationConnectorImpl])
trait ValidationConnector {

  def postXml(messageType: MessageType, stream: Source[ByteString, ?])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[XmlValidationErrorResponse]]

  def postJson(messageType: MessageType, stream: Source[ByteString, ?])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[JsonValidationErrorResponse]]

}

@Singleton
class ValidationConnectorImpl @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig, val metrics: MetricRegistry)
    extends ValidationConnector
    with HasMetrics
    with V2BaseConnector
    with DefaultBodyWritables
    with JsonBodyWritables
    with Logging {

  override def postXml(messageType: MessageType, stream: Source[ByteString, ?])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[XmlValidationErrorResponse]] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        post[XmlValidationErrorResponse](messageType, stream, MimeTypes.XML)
    }

  override def postJson(messageType: MessageType, stream: Source[ByteString, ?])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[JsonValidationErrorResponse]] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        post[JsonValidationErrorResponse](messageType, stream, MimeTypes.JSON)
    }

  private def post[A](messageType: MessageType, stream: Source[ByteString, ?], contentType: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    reads: Reads[A]
  ): Future[Option[A]] = {
    val url = appConfig.validatorUrl.withPath(validationRoute(messageType))

    httpClientV2
      .post(url"$url")
      .setHeader(HeaderNames.CONTENT_TYPE -> contentType)
      .withBody(stream)
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case NO_CONTENT => Future.successful(None)
            case OK =>
              response
                .as[A]
                .map(
                  response => Some(response)
                )
            case _ => response.error
          }
      }
  }

}
