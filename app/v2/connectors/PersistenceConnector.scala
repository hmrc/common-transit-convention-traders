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
import io.lemonlabs.uri.Url
import metrics.HasMetrics
import metrics.MetricsKeys
import config.Constants
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.OK
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.request.MessageType
import v2.models.responses.MessageSummary
import v2.models.responses.MovementResponse
import v2.models.responses.MovementSummary
import v2.models.responses.UpdateMovementResponse

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[PersistenceConnectorImpl])
trait PersistenceConnector {

  def postMovement(eori: EORINumber, movementType: MovementType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MovementResponse]

  def getMessage(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MessageSummary]

  def getMessages(eori: EORINumber, movementType: MovementType, movementId: MovementId, receivedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[MessageSummary]]

  def getMovement(eori: EORINumber, movementType: MovementType, movementId: MovementId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MovementSummary]

  def getMovements(eori: EORINumber, movementType: MovementType, updatedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[MovementSummary]]

  def postMessage(movementId: MovementId, messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[UpdateMovementResponse]

}

@Singleton
class PersistenceConnectorImpl @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig, val metrics: Metrics)
    extends PersistenceConnector
    with HasMetrics
    with V2BaseConnector
    with Logging {

  override def postMovement(eori: EORINumber, movementType: MovementType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MovementResponse] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        val url = appConfig.movementsUrl.withPath(postMovementUrl(eori, movementType))

        httpClientV2
          .post(url"$url")
          .addHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          .withBody(source)
          .execute[HttpResponse]
          .flatMap {
            response =>
              response.status match {
                case OK => response.as[MovementResponse]
                case _ =>
                  response.error
              }
          }
    }

  override def getMessage(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MessageSummary] = {
    val url = appConfig.movementsUrl.withPath(getMessageUrl(eori, movementType, movementId, messageId))
    httpClientV2
      .get(url"$url")
      .addHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case OK => response.as[MessageSummary]
            case _  => response.error
          }
      }
  }

  override def getMessages(eori: EORINumber, movementType: MovementType, movementId: MovementId, receivedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[MessageSummary]] = {
    val url =
      withDateTimeParameter(
        appConfig.movementsUrl.withPath(getMessagesUrl(eori, movementType, movementId)).toUrl,
        "receivedSince",
        receivedSince
      )

    httpClientV2
      .get(url"$url")
      .addHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case OK => response.as[Seq[MessageSummary]]
            case _  => response.error
          }
      }
  }

  override def getMovement(eori: EORINumber, movementType: MovementType, movementId: MovementId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MovementSummary] = {
    val url = appConfig.movementsUrl.withPath(getMovementUrl(eori, movementType, movementId))

    httpClientV2
      .get(url"$url")
      .addHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case OK => response.as[MovementSummary]
            case _  => response.error
          }
      }
  }

  override def getMovements(eori: EORINumber, movementType: MovementType, updatedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[MovementSummary]] = {
    val url = withDateTimeParameter(
      appConfig.movementsUrl.withPath(getAllMovementsUrl(eori, movementType)),
      "updatedSince",
      updatedSince
    )

    httpClientV2
      .get(url"$url")
      .addHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case OK => response.as[Seq[MovementSummary]]
            case _  => response.error
          }
      }
  }

  override def postMessage(movementId: MovementId, messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[UpdateMovementResponse] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        val url = appConfig.movementsUrl.withPath(postMessageUrl(movementId))
        httpClientV2
          .post(url"$url")
          .addHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.XML, Constants.XMessageTypeHeader -> messageType.code)
          .withBody(source)
          .execute[HttpResponse]
          .flatMap {
            response =>
              response.status match {
                case OK => response.as[UpdateMovementResponse]
                case _  => response.error
              }
          }
    }

  private def withDateTimeParameter(urlPath: Url, queryName: String, dateTime: Option[OffsetDateTime]) =
    dateTime
      .map(
        time => urlPath.addParam(queryName, DateTimeFormatter.ISO_DATE_TIME.format(time))
      )
      .getOrElse(urlPath)

}
