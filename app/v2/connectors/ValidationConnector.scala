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
import play.api.http.Status.NO_CONTENT
import play.api.http.Status.OK
import play.api.libs.json.Reads
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.request.MessageType
import v2.models.responses.Json
import v2.models.responses.Xml

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[ValidationConnectorImpl])
trait ValidationConnector {

  def postXml(messageType: MessageType, xmlStream: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[Xml]]

  def postJson(messageType: MessageType, xmlStream: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[Json]]

}

@Singleton
class ValidationConnectorImpl @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig, val metrics: Metrics)
    extends ValidationConnector
    with HasMetrics
    with V2BaseConnector
    with Logging {

  override def postXml(messageType: MessageType, stream: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[Xml]] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        post[Xml](messageType, stream, MimeTypes.XML)
    }

  override def postJson(messageType: MessageType, stream: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[Json]] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        post[Json](messageType, stream, MimeTypes.JSON)
    }

  private def post[A](messageType: MessageType, stream: Source[ByteString, _], contentType: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    reads: Reads[A]
  ): Future[Option[A]] = {
    val url = appConfig.validatorUrl.withPath(validationRoute(messageType))

    httpClientV2
      .post(url"$url")
      .transform(_.addHttpHeaders(HeaderNames.CONTENT_TYPE -> contentType))
      .withBody(stream)
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case NO_CONTENT => Future.successful(None)
            case OK         => response.as[A].map(Option(_))
            case _          => response.error

          }
      }
  }
}
