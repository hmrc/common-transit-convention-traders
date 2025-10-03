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
import com.google.inject.Singleton
import config.AppConfig
import config.Constants
import io.lemonlabs.uri.Url
import io.lemonlabs.uri.UrlPath
import io.lemonlabs.uri.config.ExcludeNones
import metrics.HasMetrics
import metrics.MetricsKeys
import models.common.*
import models.Version
import models.request.MessageType
import models.request.MessageUpdate
import models.responses.*
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.CREATED
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.libs.ws.DefaultBodyWritables
import play.api.libs.ws.JsonBodyWritables
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class PersistenceConnector @Inject() (httpClientV2: HttpClientV2, val metrics: MetricRegistry)(implicit appConfig: AppConfig)
    extends BaseConnector
    with HasMetrics
    with DefaultBodyWritables
    with JsonBodyWritables
    with Logging {

  private val movementsBaseRoute: String = "/transit-movements"

  def postMovement(eori: EORINumber, movementType: MovementType, source: Option[Source[ByteString, ?]], version: Version)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MovementResponse] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        val url = appConfig.movementsUrl.withPath(postMovementUrl(eori, movementType))

        val httpClient = httpClientV2
          .post(url"$url")
          .withInternalAuthToken
          .setHeader("APIVersion" -> s"${version.value}")
          .withClientId

        (source match {
          case Some(src) => httpClient.setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.XML).withBody(src)
          case None      => httpClient
        }).executeAndDeserialise[MovementResponse]
    }

  def getMessage(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId, version: Version)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MessageSummary] = {
    val url = appConfig.movementsUrl.withPath(getMessageUrl(eori, movementType, movementId, messageId))
    httpClientV2
      .get(url"$url")
      .withInternalAuthToken
      .setHeader("APIVersion" -> s"${version.value}")
      .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .executeAndDeserialise[MessageSummary]
  }

  def getMessages(
    eori: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    receivedSince: Option[OffsetDateTime],
    page: Option[PageNumber] = Some(PageNumber(1)),
    count: ItemCount,
    receivedUntil: Option[OffsetDateTime],
    version: Version
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[PaginationMessageSummary] = {

    val url =
      withParameters(
        urlPath = appConfig.movementsUrl.withPath(getMessagesUrl(eori, movementType, movementId)),
        receivedSince = receivedSince,
        page = page,
        count = count,
        receivedUntil = receivedUntil
      )

    httpClientV2
      .get(url"$url")
      .withInternalAuthToken
      .setHeader("APIVersion" -> s"${version.value}")
      .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .executeAndDeserialise[PaginationMessageSummary]
  }

  def getMovement(eori: EORINumber, movementType: MovementType, movementId: MovementId, version: Version)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MovementSummary] = {
    val url = appConfig.movementsUrl.withPath(getMovementUrl(eori, movementType, movementId))

    httpClientV2
      .get(url"$url")
      .withInternalAuthToken
      .setHeader("APIVersion" -> s"${version.value}")
      .setHeader(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .executeAndDeserialise[MovementSummary]
  }

  def getMovements(
    eori: EORINumber,
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber],
    page: Option[PageNumber] = Some(PageNumber(1)),
    count: ItemCount,
    receivedUntil: Option[OffsetDateTime],
    localReferenceNumber: Option[LocalReferenceNumber],
    version: Version
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[PaginationMovementSummary] = {

    val urlWithOptions = withParameters(
      urlPath = appConfig.movementsUrl.withPath(getAllMovementsUrl(eori, movementType)),
      updatedSince = updatedSince,
      movementEORI = movementEORI,
      movementReferenceNumber = movementReferenceNumber,
      page = page,
      count = count,
      receivedUntil = receivedUntil,
      localReferenceNumber = localReferenceNumber
    )

    httpClientV2
      .get(url"$urlWithOptions")
      .withInternalAuthToken
      .setHeader("APIVersion" -> s"${version.value}")
      .setHeader(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .executeAndDeserialise[PaginationMovementSummary]
  }

  def postMessage(movementId: MovementId, messageType: Option[MessageType], source: Option[Source[ByteString, ?]], version: Version)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[UpdateMovementResponse] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        val url = appConfig.movementsUrl.withPath(postMessageUrl(movementId))

        val request = httpClientV2
          .post(url"$url")
          .withInternalAuthToken
          .setHeader("APIVersion" -> s"${version.value}")

        source match {
          case None =>
            request
              .executeAndDeserialise[UpdateMovementResponse]
          case Some(source) =>
            request
              .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
              .setHeader(Constants.XMessageTypeHeader -> messageType.get.code)
              .withBody(source)
              .executeAndDeserialise[UpdateMovementResponse]
        }
    }

  private def withParameters(
    urlPath: Url,
    updatedSince: Option[OffsetDateTime] = None,
    movementEORI: Option[EORINumber] = None,
    movementReferenceNumber: Option[MovementReferenceNumber] = None,
    receivedSince: Option[OffsetDateTime] = None,
    page: Option[PageNumber],
    count: ItemCount,
    receivedUntil: Option[OffsetDateTime],
    localReferenceNumber: Option[LocalReferenceNumber] = None
  ) = {

    val pageNumberValid = page.fold(Some(PageNumber(1)))(Some(_)) // not a Zero based index.

    urlPath
      .withConfig(urlPath.config.copy(renderQuery = ExcludeNones))
      .addParams(
        "updatedSince" -> updatedSince.map(
          time => DateTimeFormatter.ISO_DATE_TIME.format(time)
        ),
        "movementEORI"            -> movementEORI.map(_.value),
        "movementReferenceNumber" -> movementReferenceNumber.map(_.value),
        "localReferenceNumber"    -> localReferenceNumber.map(_.value),
        "receivedSince"           -> receivedSince.map(
          time => DateTimeFormatter.ISO_DATE_TIME.format(time)
        ),
        "page"          -> pageNumberValid.map(_.value.toString),
        "count"         -> Some(count.value.toString),
        "receivedUntil" -> receivedUntil.map(
          time => DateTimeFormatter.ISO_DATE_TIME.format(time)
        )
      )
  }

  def patchMessage(
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    body: MessageUpdate,
    version: Version
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] = {
    val url = appConfig.movementsUrl.withPath(updateMessageRoute(eoriNumber, movementType, movementId, messageId))

    httpClientV2
      .patch(url"$url")
      .withInternalAuthToken
      .setHeader("APIVersion" -> s"${version.value}")
      .withBody(Json.toJson(body))
      .executeAndExpect(OK)
  }

  def updateMessageBody(
    messageType: MessageType,
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    source: Source[ByteString, ?],
    version: Version
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] = {
    val url = appConfig.movementsUrl.withPath(messageBodyUrl(eoriNumber, movementType, movementId, messageId))

    httpClientV2
      .post(url"$url")
      .withInternalAuthToken
      .setHeader("APIVersion" -> s"${version.value}")
      .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .setHeader(Constants.XMessageTypeHeader -> messageType.code)
      .withBody(source)
      .executeAndExpect(CREATED)
  }

  def getMessageBody(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId, version: Version)(implicit
    hc: HeaderCarrier,
    mat: Materializer,
    ec: ExecutionContext
  ): Future[Source[ByteString, ?]] = {

    val url = appConfig.movementsUrl.withPath(messageBodyUrl(eoriNumber, movementType, movementId, messageId))

    httpClientV2
      .get(url"$url")
      .withInternalAuthToken
      .setHeader("APIVersion" -> s"${version.value}")
      .setHeader(HeaderNames.ACCEPT -> MimeTypes.XML)
      .executeAsStream
  }

  private def postMovementUrl(eoriNumber: EORINumber, movementType: MovementType): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}")

  private def postMessageUrl(movementId: MovementId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/movements/${movementId.value}/messages")

  private def messageBodyUrl(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}/body")

  private def getMessageUrl(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}")

  private def getMessagesUrl(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages")

  private def getMovementUrl(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}")

  private def getAllMovementsUrl(eoriNumber: EORINumber, movementType: MovementType): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}")

  private def updateMessageRoute(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}")

}
