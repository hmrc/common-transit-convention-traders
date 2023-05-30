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

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import config.Constants
import io.lemonlabs.uri.Url
import io.lemonlabs.uri.config.ExcludeNones
import metrics.HasMetrics
import metrics.MetricsKeys
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementReferenceNumber
import v2.models.MovementType
import v2.models.request.MessageType
import v2.models.request.MessageUpdate
import v2.models.responses.MessageSummary
import v2.models.responses.MovementResponse
import v2.models.responses.MovementSummary
import v2.models.responses.UpdateMovementResponse

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import play.api.http.Status.CREATED

@ImplementedBy(classOf[PersistenceConnectorImpl])
trait PersistenceConnector {

  def postMovement(eori: EORINumber, movementType: MovementType, source: Option[Source[ByteString, _]])(implicit
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

  def getMovements(
    eori: EORINumber,
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[MovementSummary]]

  def postMessage(movementId: MovementId, messageType: Option[MessageType], source: Option[Source[ByteString, _]])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[UpdateMovementResponse]

  def patchMessage(
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    body: MessageUpdate
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit]

  def updateMessageBody(
    messageType: MessageType,
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    source: Source[ByteString, _]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit]

  def getMessageBody(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    mat: Materializer,
    ec: ExecutionContext
  ): Future[Source[ByteString, _]]

}

@Singleton
class PersistenceConnectorImpl @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig, val metrics: Metrics)
    extends PersistenceConnector
    with HasMetrics
    with V2BaseConnector
    with Logging {

  override def postMovement(eori: EORINumber, movementType: MovementType, source: Option[Source[ByteString, _]])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MovementResponse] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        val url = appConfig.movementsUrl.withPath(postMovementUrl(eori, movementType))

        val httpClient = httpClientV2
          .post(url"$url")

        (source match {
          case Some(src) => httpClient.setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.XML).withBody(src)
          case None      => httpClient
        }).executeAndDeserialise[MovementResponse]
    }

  override def getMessage(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MessageSummary] = {
    val url = appConfig.movementsUrl.withPath(getMessageUrl(eori, movementType, movementId, messageId))
    httpClientV2
      .get(url"$url")
      .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .executeAndDeserialise[MessageSummary]
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
      .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .executeAndDeserialise[Seq[MessageSummary]]
  }

  override def getMovement(eori: EORINumber, movementType: MovementType, movementId: MovementId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MovementSummary] = {
    val url = appConfig.movementsUrl.withPath(getMovementUrl(eori, movementType, movementId))

    httpClientV2
      .get(url"$url")
      .setHeader(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .executeAndDeserialise[MovementSummary]
  }

  override def getMovements(
    eori: EORINumber,
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[MovementSummary]] = {
    val urlWithOptions = withParameters(
      appConfig.movementsUrl.withPath(getAllMovementsUrl(eori, movementType)),
      updatedSince,
      movementEORI,
      movementReferenceNumber
    )

    httpClientV2
      .get(url"$urlWithOptions")
      .setHeader(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .executeAndDeserialise[Seq[MovementSummary]]
  }

  override def postMessage(movementId: MovementId, messageType: Option[MessageType], source: Option[Source[ByteString, _]])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[UpdateMovementResponse] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        val url = appConfig.movementsUrl.withPath(postMessageUrl(movementId))

        val request = httpClientV2
          .post(url"$url")

        source match {
          case None =>
            request
              .executeAndDeserialise[UpdateMovementResponse]
          case Some(source) =>
            request
              .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.XML, Constants.XMessageTypeHeader -> messageType.get.code)
              .withBody(source)
              .executeAndDeserialise[UpdateMovementResponse]
        }
    }

  private def withDateTimeParameter(urlPath: Url, queryName: String, dateTime: Option[OffsetDateTime]) =
    dateTime
      .map(
        time => urlPath.addParam(queryName, DateTimeFormatter.ISO_DATE_TIME.format(time))
      )
      .getOrElse(urlPath)

  private def withParameters(
    urlPath: Url,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber]
  ) =
    urlPath
      .withConfig(urlPath.config.copy(renderQuery = ExcludeNones))
      .addParam(
        "updatedSince",
        updatedSince.map(
          time => DateTimeFormatter.ISO_DATE_TIME.format(time)
        )
      )
      .addParam("movementEORI", movementEORI.map(_.value))
      .addParam("movementReferenceNumber", movementReferenceNumber.map(_.value))

  def patchMessage(
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    body: MessageUpdate
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] = {
    val url = appConfig.movementsUrl.withPath(updateMessageRoute(eoriNumber, movementType, movementId, messageId))

    httpClientV2
      .patch(url"$url")
      .withBody(Json.toJson(body))
      .executeAndExpect(OK)
  }

  override def updateMessageBody(
    messageType: MessageType,
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    source: Source[ByteString, _]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] = {
    val url = appConfig.movementsUrl.withPath(messageBodyUrl(eoriNumber, movementType, movementId, messageId))

    httpClientV2
      .post(url"$url")
      .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.XML, Constants.XMessageTypeHeader -> messageType.code)
      .withBody(source)
      .executeAndExpect(CREATED)
  }

  override def getMessageBody(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    mat: Materializer,
    ec: ExecutionContext
  ): Future[Source[ByteString, _]] = {

    val url = appConfig.movementsUrl.withPath(messageBodyUrl(eoriNumber, movementType, movementId, messageId))

    httpClientV2
      .get(url"$url")
      .setHeader(HeaderNames.ACCEPT -> MimeTypes.XML)
      .executeAsStream
  }
}
